package io.retel.ariproxy;

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
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.retel.ariproxy.boundary.callcontext.CallContextProvider;
import io.retel.ariproxy.boundary.commandsandresponses.AriCommandResponseKafkaProcessor;
import io.retel.ariproxy.boundary.events.WebsocketMessageToProducerRecordTranslator;
import io.retel.ariproxy.config.ConfigLoader;
import io.retel.ariproxy.config.ServiceConfig;
import io.retel.ariproxy.health.HealthService;
import io.retel.ariproxy.metrics.MetricsService;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class Main {

	static {
		System.setProperty("log4j.shutdownCallbackRegistry", "com.djdch.log4j.StaticShutdownCallbackRegistry");
	}

	private static final ServiceConfig config = ConfigLoader.load();

	public static void main(String[] args) {
		final ActorSystem system = ActorSystem.create(config.getName());

		system.registerOnTermination(() -> System.exit(0));

		system.actorOf(HealthService.props(config.getHttpPort()), HealthService.ACTOR_NAME);

		final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(64_000, 21_600_000));
		final ActorRef metricsService = system.actorOf(MetricsService.props());

		runAriEventProcessor(config, system, callContextProvider, metricsService, system::terminate);

		runAriCommandResponseProcessor(config, system, callContextProvider, metricsService);
	}

	private static ActorMaterializer runAriCommandResponseProcessor(
			ServiceConfig config,
			ActorSystem system,
			ActorRef callContextProvider,
			ActorRef metricsService) {
		final ConsumerSettings<String, String> consumerSettings = ConsumerSettings
				.create(system, new StringDeserializer(), new StringDeserializer())
				.withBootstrapServers(config.getKafkaBootstrapServers())
				.withGroupId(config.getKafkaConsumerGroup())
				.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
				.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		final ProducerSettings<String, String> producerSettings = ProducerSettings
				.create(system, new StringSerializer(), new StringSerializer())
				.withBootstrapServers(config.getKafkaBootstrapServers());

		final Source<ConsumerRecord<String, String>, NotUsed> source = Consumer
				.plainSource(consumerSettings, Subscriptions.topics(config.getKafkaCommandsTopic()))
				.mapMaterializedValue(control -> NotUsed.getInstance());

		final Sink<ProducerRecord<String, String>, NotUsed> sink = Producer
				.plainSink(producerSettings)
				.mapMaterializedValue(done -> NotUsed.getInstance());

		return AriCommandResponseKafkaProcessor.commandResponseProcessing()
				.withConfig(config)
				.on(system)
				.withHandler(requestAndContext -> Http.get(system).singleRequest(requestAndContext._1))
				.withCallContextProvider(callContextProvider)
				.withMetricsService(metricsService)
				.from(source)
				.to(sink)
				.run();
	}

	private static ActorMaterializer runAriEventProcessor(
			ServiceConfig config,
			ActorSystem system,
			ActorRef callContextProvider,
			ActorRef metricsService,
			Runnable applicationReplacedHandler) {
		// see: https://doc.akka.io/docs/akka/2.5.8/java/stream/stream-error.html#delayed-restarts-with-a-backoff-stage
		final Flow<Message, Message, NotUsed> restartWebsocketFlow = RestartFlow.withBackoff(
				Duration.ofSeconds(3), // min backoff
				Duration.ofSeconds(30), // max backoff
				0.2, // adds 20% "noise" to vary the intervals slightly
				() -> createWebsocketFlow(system, config.getWebsocketUri())
		);

		final Source<Message, NotUsed> source = Source.<Message>maybe().viaMat(restartWebsocketFlow, Keep.right());

		final ProducerSettings<String, String> producerSettings = ProducerSettings
				.create(system, new StringSerializer(), new StringSerializer())
				.withBootstrapServers(config.getKafkaBootstrapServers());

		final Sink<ProducerRecord<String, String>, NotUsed> sink = Producer
				.plainSink(producerSettings)
				.mapMaterializedValue(done -> NotUsed.getInstance());

		return WebsocketMessageToProducerRecordTranslator.eventProcessing()
				.withConfig(config)
				.on(system)
				.withHandler(applicationReplacedHandler)
				.withCallContextProvider(callContextProvider)
				.withMetricsService(metricsService)
				.from(source)
				.to(sink)
				.run();
	}

	// NOTE: We need this method because the resulting flow can only be materialized once;
	// see: https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#websocketclientflow
	private static Flow<Message, Message, CompletionStage<WebSocketUpgradeResponse>> createWebsocketFlow(
			ActorSystem system,
			String websocketUri) {
		return Http.get(system).webSocketClientFlow(WebSocketRequest.create(websocketUri));
	}
}
