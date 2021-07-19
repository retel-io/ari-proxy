package io.retel.ariproxy.boundary.events;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.ws.Message;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.retel.ariproxy.akkajavainterop.PatternsAdapter;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageEnvelope;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResource;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.StartCallSetupTimer;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;

public class AriEventProcessing {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final ObjectReader reader = mapper.reader();
  private static final ObjectWriter writer = mapper.writerFor(AriMessageEnvelope.class);
  // Note: This timeout is pretty high right now as the initial redis interaction takes quite some
  // time...
  private static final int PROVIDE_CALLCONTEXT_TIMEOUT = 1000;

  public static Seq<MetricsGatherer> determineMetricsGatherer(AriMessageType type) {

    List<MetricsGatherer> metricsGatherers =
        List.of(callContextSupplier -> new IncreaseCounter(type.name(), null)); // TODO

    switch (type) {
      case STASIS_START:
        metricsGatherers =
            metricsGatherers.appendAll(
                List.of(
                    callContextSupplier -> new IncreaseCounter("CallsStarted", null), // TODO
                    callContextSupplier -> new StartCallSetupTimer(callContextSupplier.get())));
        break;
      case STASIS_END:
        metricsGatherers =
            metricsGatherers.append(
                callContextSupplier -> new IncreaseCounter("CallsEnded", null)); // TODO
        break;
    }

    return metricsGatherers;
  }

  public static Source<ProducerRecord<String, String>, NotUsed> generateProducerRecordFromEvent(
      String kafkaCommandsTopic,
      String kafkaEventsAndResponsesTopic,
      Message message,
      ActorRef callContextProvider,
      LoggingAdapter log,
      Runnable applicationReplacedHandler) {

    final JsonNode messageBody =
        Try.of(() -> reader.readTree(message.asTextMessage().getStrictText()))
            .getOrElseThrow(t -> new RuntimeException(t));

    final String eventTypeString =
        getValueFromMessageByPath(message, "/type")
            .getOrElseThrow(() -> new RuntimeException(message.asTextMessage().getStrictText()));
    final AriMessageType ariMessageType = AriMessageType.fromType(eventTypeString);

    if (AriMessageType.APPLICATION_REPLACED.equals(ariMessageType)) {
      log.info("Got APPLICATION_REPLACED event, shutting down...");
      applicationReplacedHandler.run();
      return Source.empty();
    }

    final Option<String> maybeCallContextFromChannelVars =
        getValueFromMessageByPath(message, "/channel/channelvars/CALL_CONTEXT");

    return ariMessageType
        .extractResourceIdFromBody(messageBody)
        .map(
            resourceIdTry ->
                resourceIdTry.flatMap(
                    resourceId -> {
                      final Try<String> maybeCallContext =
                          getCallContext(
                              resourceId,
                              callContextProvider,
                              maybeCallContextFromChannelVars,
                              AriMessageType.STASIS_START.equals(ariMessageType)
                                  ? ProviderPolicy.CREATE_IF_MISSING
                                  : ProviderPolicy.LOOKUP_ONLY);
                      return maybeCallContext.flatMap(
                          callContext ->
                              createProducerRecord(
                                      kafkaCommandsTopic,
                                      kafkaEventsAndResponsesTopic,
                                      ariMessageType,
                                      resourceId,
                                      log,
                                      callContext,
                                      messageBody)
                                  .map(Source::single));
                    }))
        .toTry()
        .flatMap(Function.identity())
        .getOrElseThrow(t -> new RuntimeException(t));
  }

  private static Try<ProducerRecord<String, String>> createProducerRecord(
      final String kafkaCommandsTopic,
      final String kafkaEventsAndResponsesTopic,
      final AriMessageType messageType,
      final String resourceId,
      final LoggingAdapter log,
      final String callContext,
      final JsonNode messageBody) {

    final java.util.List<AriResource> resources =
        messageType
            .getResourceType()
            .map(resourceType -> new AriResource(resourceType, resourceId))
            .toJavaList();
    final AriMessageEnvelope envelope =
        new AriMessageEnvelope(
            messageType, kafkaCommandsTopic, messageBody, callContext, resources);

    return Try.of(() -> writer.writeValueAsString(envelope))
        .map(
            marshalledEnvelope -> {
              log.debug("[ARI MESSAGE TYPE] {}", envelope.getType());
              return new ProducerRecord<>(
                  kafkaEventsAndResponsesTopic, callContext, marshalledEnvelope);
            });
  }

  public static Try<String> getCallContext(
      String resourceId,
      ActorRef callContextProvider,
      final Option<String> maybeCallContextFromChannelVars,
      ProviderPolicy providerPolicy) {
    return PatternsAdapter.<CallContextProvided>ask(
            callContextProvider,
            new ProvideCallContext(resourceId, maybeCallContextFromChannelVars, providerPolicy),
            PROVIDE_CALLCONTEXT_TIMEOUT)
        .map(CallContextProvided::callContext)
        .toTry();
  }

  public static Option<String> getValueFromMessageByPath(Message message, String path) {
    return Try.of(() -> reader.readTree(message.asTextMessage().getStrictText()))
        .map(root -> root.at(path))
        .map(JsonNode::asText)
        .filter(StringUtils::isNotBlank)
        .toOption();
  }
}

@FunctionalInterface
interface MetricsGatherer {

  Object withCallContextSupplier(Supplier<String> callContextSupplier);
}
