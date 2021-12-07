package io.retel.ariproxy.boundary.events;

import static io.retel.ariproxy.boundary.events.AriEventProcessing.generateProducerRecordFromEvent;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
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
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.metrics.Metrics;
import org.apache.kafka.clients.producer.ProducerRecord;

public class WebsocketMessageToProducerRecordTranslator {

  private static final String SERVICE = "service";
  private static final String KAFKA = "kafka";
  private static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";
  private static final String COMMANDS_TOPIC = "commands-topic";

  private static final Attributes LOG_LEVELS =
      Attributes.createLogLevels(Logging.InfoLevel(), Logging.InfoLevel(), Logging.ErrorLevel());

  public static RunnableGraph<NotUsed> eventProcessing(
      final ActorSystem<?> system,
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final Source<Message, NotUsed> source,
      final Sink<ProducerRecord<String, String>, NotUsed> sink,
      final Runnable applicationReplacedHandler) {
    final Function<Throwable, Supervision.Directive> decider =
        t -> {
          system.log().error("WebsocketMessageToProducerRecordTranslator stream failed", t);
          Metrics.countEventProcessorRestart();
          return (Supervision.Directive) Supervision.resume();
        };

    final Config kafkaConfig = ConfigFactory.load().getConfig(SERVICE).getConfig(KAFKA);
    final String commandsTopic = kafkaConfig.getString(COMMANDS_TOPIC);
    final String eventsAndResponsesTopic = kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC);

    return source
        .flatMapConcat(
            (msg) ->
                generateProducerRecordFromEvent(
                    commandsTopic,
                    eventsAndResponsesTopic,
                    msg,
                    callContextProvider,
                    system.log(),
                    applicationReplacedHandler,
                    system))
        .log(">>>   ARI EVENT", ProducerRecord::value)
        .withAttributes(LOG_LEVELS)
        .to(sink)
        .withAttributes(ActorAttributes.withSupervisionStrategy(decider));
  }
}
