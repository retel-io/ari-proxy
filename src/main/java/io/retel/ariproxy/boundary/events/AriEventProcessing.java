package io.retel.ariproxy.boundary.events;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.model.ws.Message;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.retel.ariproxy.boundary.callcontext.api.*;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageEnvelope;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResource;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.MetricsServiceMessage;
import io.retel.ariproxy.metrics.StartCallSetupTimer;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;

public class AriEventProcessing {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final ObjectReader reader = mapper.reader();
  private static final ObjectWriter writer = mapper.writerFor(AriMessageEnvelope.class);
  // Note: This timeout is pretty high right now as the initial redis interaction takes quite some
  // time...
  private static final Duration PROVIDE_CALLCONTEXT_TIMEOUT = Duration.ofMillis(1000);

  public static Seq<MetricsGatherer> determineMetricsGatherer(AriMessageType type) {

    List<MetricsGatherer> metricsGatherers =
        List.of(callContextSupplier -> new IncreaseCounter(type.name()));

    switch (type) {
      case STASIS_START:
        metricsGatherers =
            metricsGatherers.appendAll(
                List.of(
                    callContextSupplier -> new IncreaseCounter("CallsStarted"),
                    callContextSupplier -> new StartCallSetupTimer(callContextSupplier.get())));
        break;
      case STASIS_END:
        metricsGatherers =
            metricsGatherers.append(callContextSupplier -> new IncreaseCounter("CallsEnded"));
        break;
    }

    return metricsGatherers;
  }

  public static Source<ProducerRecord<String, String>, NotUsed> generateProducerRecordFromEvent(
      final String kafkaCommandsTopic,
      final String kafkaEventsAndResponsesTopic,
      final Message message,
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final Logger log,
      final Runnable applicationReplacedHandler,
      final ActorSystem<?> system) {

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
                      final ProviderPolicy providerPolicy = AriMessageType.STASIS_START.equals(ariMessageType)
                          ? ProviderPolicy.CREATE_IF_MISSING
                          : ProviderPolicy.LOOKUP_ONLY;
                      final Try<String> maybeCallContext =
                          getCallContext(
                              resourceId,
                              callContextProvider,
                              maybeCallContextFromChannelVars,
                              providerPolicy,
                              system);
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
      final Logger log,
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
      final String resourceId,
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final Option<String> maybeCallContextFromChannelVars,
      final ProviderPolicy providerPolicy,
      final ActorSystem<?> system) {

    return Try.of(
        () -> {
          final CallContextProvided response =
              AskPattern.<CallContextProviderMessage, CallContextProvided>askWithStatus(
                      callContextProvider,
                      replyTo ->
                          new ProvideCallContext(
                              resourceId, providerPolicy, maybeCallContextFromChannelVars, replyTo),
                      PROVIDE_CALLCONTEXT_TIMEOUT,
                      system.scheduler())
                  .toCompletableFuture()
                  .get();

          return response.callContext();
        });
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
  MetricsServiceMessage withCallContextSupplier(Supplier<String> callContextSupplier);
}
