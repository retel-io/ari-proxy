package io.retel.ariproxy;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.API.run;
import static io.vavr.Patterns.$Failure;
import static io.vavr.Patterns.$Success;
import static io.vavr.Predicates.instanceOf;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.Producer;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartFlow;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.CallContextProvider;
import io.retel.ariproxy.boundary.commandsandresponses.AriCommandResponseKafkaProcessor;
import io.retel.ariproxy.boundary.events.WebsocketMessageToProducerRecordTranslator;
import io.retel.ariproxy.boundary.processingpipeline.Run;
import io.retel.ariproxy.health.HealthService;
import io.retel.ariproxy.metrics.MetricsService;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class Main {

	private static final String BOOTSTRAP_SERVERS = "bootstrap-servers";
	private static final String WEBSOCKET_URI = "websocket-uri";
	private static final String CONSUMER_GROUP = "consumer-group";
	private static final String COMMANDS_TOPIC = "commands-topic";
	private static final String SERVICE = "service";
	private static final String NAME = "name";
	private static final String HTTPPORT = "httpport";
	public static final String KAFKA = "kafka";

	static {
		System.setProperty("log4j.shutdownCallbackRegistry", "com.djdch.log4j.StaticShutdownCallbackRegistry");
	}

	public static void main(String[] args) {

		final Config serviceConfig = ConfigFactory.load().getConfig(SERVICE);

		final ActorSystem system = ActorSystem.create(serviceConfig.getString(NAME));

		system.registerOnTermination(() -> System.exit(0));

		system.actorOf(HealthService.props(serviceConfig.getInt(HTTPPORT)), HealthService.ACTOR_NAME);

		final ActorRef metricsService = system.actorOf(MetricsService.props());
		final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(metricsService));

		runAriEventProcessor(serviceConfig.getConfig(KAFKA), system, callContextProvider, metricsService, system::terminate);

		runAriCommandResponseProcessor(serviceConfig, system, callContextProvider, metricsService);
	}

	private static ActorMaterializer runAriCommandResponseProcessor(
			Config kafkaConfig,
			ActorSystem system,
			ActorRef callContextProvider,
			ActorRef metricsService) {
		final ConsumerSettings<String, String> consumerSettings = ConsumerSettings
				.create(system, new StringDeserializer(), new StringDeserializer())
				.withBootstrapServers(kafkaConfig.getString(BOOTSTRAP_SERVERS))
				.withGroupId(kafkaConfig.getString(CONSUMER_GROUP))
				.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
				.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		final ProducerSettings<String, String> producerSettings = ProducerSettings
				.create(system, new StringSerializer(), new StringSerializer())
				.withBootstrapServers(kafkaConfig.getString(BOOTSTRAP_SERVERS));

		final Source<ConsumerRecord<String, String>, NotUsed> source = RestartSource.withBackoff(
				Duration.of(5, ChronoUnit.SECONDS),
				Duration.of(10, ChronoUnit.SECONDS),
				0.2,
				() -> Consumer
						.plainSource(consumerSettings, Subscriptions.topics(kafkaConfig.getString(COMMANDS_TOPIC)))
						.mapMaterializedValue(control -> NotUsed.getInstance())
		);

		final Sink<ProducerRecord<String, String>, NotUsed> sink = Producer
				.plainSink(producerSettings)
				.mapMaterializedValue(done -> NotUsed.getInstance());

		return AriCommandResponseKafkaProcessor.commandResponseProcessing()
				.on(system)
				.withHandler(requestAndContext -> Http.get(system).singleRequest(requestAndContext._1))
				.withCallContextProvider(callContextProvider)
				.withMetricsService(metricsService)
				.from(source)
				.to(sink)
				.run();
	}

	private static void runAriEventProcessor(
			Config serviceConfig,
			ActorSystem system,
			ActorRef callContextProvider,
			ActorRef metricsService,
			Runnable applicationReplacedHandler) {
		// see: https://doc.akka.io/docs/akka/2.5.8/java/stream/stream-error.html#delayed-restarts-with-a-backoff-stage
		final Flow<Message, Message, NotUsed> restartWebsocketFlow = RestartFlow.withBackoff(
				Duration.ofSeconds(3), // min backoff
				Duration.ofSeconds(30), // max backoff
				0.2, // adds 20% "noise" to vary the intervals slightly
				() -> createWebsocketFlow(system, serviceConfig.getString(WEBSOCKET_URI))
		);

		final Source<Message, NotUsed> source = Source.<Message>maybe().viaMat(restartWebsocketFlow, Keep.right());

		final ProducerSettings<String, String> producerSettings = ProducerSettings
				.create(system, new StringSerializer(), new StringSerializer())
				.withBootstrapServers(serviceConfig.getString(BOOTSTRAP_SERVERS));

		final Sink<ProducerRecord<String, String>, NotUsed> sink = Producer
				.plainSink(producerSettings)
				.mapMaterializedValue(done -> NotUsed.getInstance());

		final Run processingPipeline = WebsocketMessageToProducerRecordTranslator.eventProcessing()
				.on(system)
				.withHandler(applicationReplacedHandler)
				.withCallContextProvider(callContextProvider)
				.withMetricsService(metricsService)
				.from(source)
				.to(sink);

		Match(Try.of(() -> processingPipeline.run())).of(
				Case($Success($()), mat -> run(() -> system.log().debug("Successfully started ari event processor."))),
				Case($Failure($(instanceOf(KafkaException.class))), err -> run(() -> {
					system.log().error(err, "Failed to start ari event processor.");
					System.exit(-1);
				}))
		);
	}

	// NOTE: We need this method because the resulting flow can only be materialized once;
	// see: https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#websocketclientflow
	private static Flow<Message, Message, CompletionStage<WebSocketUpgradeResponse>> createWebsocketFlow(
			ActorSystem system,
			String websocketUri) {
		return Http.get(system).webSocketClientFlow(WebSocketRequest.create(websocketUri));
	}
}
