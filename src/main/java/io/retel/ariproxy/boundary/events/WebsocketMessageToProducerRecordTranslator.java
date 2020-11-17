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
import akka.stream.*;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.boundary.processingpipeline.ProcessingPipeline;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.ProducerRecord;

public class WebsocketMessageToProducerRecordTranslator {

  private static final String SERVICE = "service";
  private static final String KAFKA = "kafka";
  private static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";
  private static final String COMMANDS_TOPIC = "commands-topic";

  private static final Attributes LOG_LEVELS =
      Attributes.createLogLevels(Logging.InfoLevel(), Logging.InfoLevel(), Logging.ErrorLevel());

  public static ProcessingPipeline<Message, Runnable> eventProcessing() {
    return system ->
        applicationReplacedHandler ->
            callContextProvider ->
                metricsService ->
                    source ->
                        sink ->
                            () ->
                                run(
                                    system,
                                    callContextProvider,
                                    metricsService,
                                    source,
                                    sink,
                                    applicationReplacedHandler);
  }

  private static void run(
      ActorSystem system,
      ActorRef callContextProvider,
      ActorRef metricsService,
      Source<Message, NotUsed> source,
      Sink<ProducerRecord<String, String>, NotUsed> sink,
      Runnable applicationReplacedHandler) {
    final Function<Throwable, Supervision.Directive> decider =
        t -> {
          system.log().error(t, t.getMessage());
          return (Supervision.Directive) Supervision.resume();
        };

    final Config kafkaConfig = ConfigFactory.load().getConfig(SERVICE).getConfig(KAFKA);
    final String commandsTopic = kafkaConfig.getString(COMMANDS_TOPIC);
    final String eventsAndResponsesTopic = kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC);

    source
        // .throttle(4 * 13, Duration.ofSeconds(1)) // Note: We die right now for calls/s >= 4.8
        .wireTap(Sink.foreach(msg -> gatherMetrics(msg, metricsService, callContextProvider)))
        .flatMapConcat(
            (msg) ->
                generateProducerRecordFromEvent(
                    commandsTopic,
                    eventsAndResponsesTopic,
                    msg,
                    callContextProvider,
                    system.log(),
                    applicationReplacedHandler))
        .log(">>>   ARI EVENT", record -> record.value())
        .withAttributes(LOG_LEVELS)
        .to(sink)
        .withAttributes(ActorAttributes.withSupervisionStrategy(decider))
        .run(system);
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
