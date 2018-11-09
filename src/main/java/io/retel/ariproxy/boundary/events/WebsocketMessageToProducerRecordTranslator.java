package io.retel.ariproxy.boundary.events;

import static io.retel.ariproxy.boundary.events.AriEventProcessing.determineMetricsGatherer;
import static io.retel.ariproxy.boundary.events.AriEventProcessing.generateProducerRecordFromEvent;
import static io.retel.ariproxy.boundary.events.AriEventProcessing.getCallContext;
import static io.retel.ariproxy.boundary.events.AriEventProcessing.getValueFromMessageByPath;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.http.javadsl.model.ws.Message;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Attributes;
import akka.stream.Supervision;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.boundary.processingpipeline.ProcessingPipeline;
import io.retel.ariproxy.config.ServiceConfig;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.ProducerRecord;

public class WebsocketMessageToProducerRecordTranslator {

	private static final Attributes LOG_LEVELS = Attributes
			.createLogLevels(Logging.InfoLevel(), Logging.InfoLevel(), Logging.ErrorLevel());

	public static ProcessingPipeline<Message, Runnable> eventProcessing() {
		return config -> system -> applicationReplacedHandler -> callContextProvider -> metricsService -> source -> sink -> () -> run(
				config, system, callContextProvider, metricsService, source, sink, applicationReplacedHandler);
	}

	private static ActorMaterializer run(ServiceConfig config, ActorSystem system, ActorRef callContextProvider, ActorRef metricsService,
			Source<Message, NotUsed> source, Sink<ProducerRecord<String, String>, NotUsed> sink,
			Runnable applicationReplacedHandler) {
		final Function<Throwable, Supervision.Directive> supervisorStrategy = t -> {
			system.log().error(t, t.getMessage());
			return Supervision.resume();
		};

		final ActorMaterializer materializer = ActorMaterializer.create(
				ActorMaterializerSettings.create(system).withSupervisionStrategy(supervisorStrategy),
				system);

		source
				//.throttle(4 * 13, Duration.ofSeconds(1)) // Note: We die right now for calls/s >= 4.8
				.wireTap(Sink.foreach(msg -> gatherMetrics(msg, metricsService, callContextProvider)))
				.flatMapConcat((msg) -> generateProducerRecordFromEvent(config.getKafkaCommandsTopic(), config.getKafkaEventsAndResponsesTopic(), msg, callContextProvider, system.log(),
						applicationReplacedHandler))
				.log(">>>   ARI EVENT", record -> record.value()).withAttributes(LOG_LEVELS)
				.to(sink)
				.run(materializer);

		return materializer;
	}

	private static void gatherMetrics(Message message, ActorRef metricsService, ActorRef callContextProvider) {
		final Supplier<String> callContextSupplier = () -> getValueFromMessageByPath(message, "/channel/id").toTry()
				.flatMap(channelId -> getCallContext(channelId, callContextProvider, ProviderPolicy.CREATE_IF_MISSING))
				.getOrElseThrow(t -> new RuntimeException(t.getMessage()));

		getValueFromMessageByPath(message, "/type").toOption()
				.map(type -> determineMetricsGatherer(AriMessageType.fromType(type)))
				.forEach(gatherers -> gatherers
						.forEach(gatherer -> metricsService.tell(
								// Note: This will only be evaluated if required
								gatherer.withCallContextSupplier(callContextSupplier),
								ActorRef.noSender()
						)));
	}
}
