package io.retel.ariproxy.boundary.events;

import static io.retel.ariproxy.boundary.events.AriEventProcessing.*;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.http.javadsl.model.ws.Message;
import akka.japi.function.Function;
import akka.stream.ActorAttributes;
import akka.stream.Attributes;
import akka.stream.Supervision;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.ProducerRecord;

public class WebsocketMessageToProducerRecordTranslator {
  private static final String SERVICE = "service";
  private static final String KAFKA = "kafka";
  private static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";
  private static final String COMMANDS_TOPIC = "commands-topic";

  private static final Attributes LOG_LEVELS =
      Attributes.createLogLevels(Logging.InfoLevel(), Logging.InfoLevel(), Logging.ErrorLevel());

  public static RunnableGraph<NotUsed> eventProcessing(
      final ActorSystem system,
      final Runnable applicationReplacedHandler,
      final ActorRef callContextProvider,
      final ActorRef metricsService,
      final Source<Message, NotUsed> source,
      final Sink<ProducerRecord<String, String>, NotUsed> sink) {
    final Function<Throwable, Supervision.Directive> supervisorStrategy =
        t -> {
          system.log().error(t, t.getMessage());
          return (Supervision.Directive) Supervision.resume();
        };

    final Config kafkaConfig = ConfigFactory.load().getConfig(SERVICE).getConfig(KAFKA);
    final String commandsTopic = kafkaConfig.getString(COMMANDS_TOPIC);
    final String eventsAndResponsesTopic = kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC);

    return source
        // .throttle(4 * 13, Duration.ofSeconds(1)) // Note: We die right now for calls/s >= 4.8
        .wireTap(Sink.foreach(msg -> gatherMetrics(msg, metricsService, callContextProvider)))
        .flatMapConcat(
            msg ->
                generateProducerRecordFromEvent(
                    commandsTopic,
                    eventsAndResponsesTopic,
                    msg,
                    callContextProvider,
                    system.log(),
                    applicationReplacedHandler))
        .log(">>>   ARI EVENT", record -> record.value())
        .withAttributes(LOG_LEVELS)
        .withAttributes(ActorAttributes.withSupervisionStrategy(supervisorStrategy))
        .to(sink);
  }

  private static void gatherMetrics(
      Message message, ActorRef metricsService, ActorRef callContextProvider) {
    final Supplier<String> callContextSupplier =
        () ->
            getValueFromMessageByPath(message, "/channel/id")
                .toTry()
                .flatMap(
                    channelId ->
                        getCallContext(
                            channelId, callContextProvider, ProviderPolicy.CREATE_IF_MISSING))
                .getOrElseThrow(t -> new RuntimeException(t.getMessage()));

    getValueFromMessageByPath(message, "/type")
        .toOption()
        .map(type -> determineMetricsGatherer(AriMessageType.fromType(type)))
        .forEach(
            gatherers ->
                gatherers.forEach(
                    gatherer ->
                        metricsService.tell(
                            // Note: This will only be evaluated if required
                            gatherer.withCallContextSupplier(callContextSupplier),
                            ActorRef.noSender())));
  }
}
