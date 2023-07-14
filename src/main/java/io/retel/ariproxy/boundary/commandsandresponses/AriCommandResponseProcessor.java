package io.retel.ariproxy.boundary.commandsandresponses;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.*;
import akka.actor.typed.javadsl.Behaviors;
import akka.event.Logging;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.pattern.StatusReply;
import akka.stream.*;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.AriCommandMessage;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.*;
import io.retel.ariproxy.metrics.Metrics;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AriCommandResponseProcessor {

  private static final Attributes LOG_LEVELS =
      Attributes.createLogLevels(Logging.InfoLevel(), Logging.InfoLevel(), Logging.ErrorLevel());

  private static final Logger LOGGER = LoggerFactory.getLogger(AriCommandResponseProcessor.class);

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String SERVICE = "service";
  public static final String REST = "rest";
  public static final String URI = "uri";
  public static final String PASSWORD = "password";
  public static final String USER = "user";
  public static final String KAFKA = "kafka";
  public static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";
  public static final String COMMANDS_TOPIC = "commands-topic";

  static {
    mapper.setSerializationInclusion(Include.NON_NULL);
    mapper.registerModule(new Jdk8Module());
  }

  private static final ObjectReader reader = mapper.readerFor(AriCommandEnvelope.class);
  private static final ObjectWriter ariMessageEnvelopeWriter =
      mapper.writerFor(AriMessageEnvelope.class);
  private static final ObjectWriter genericWriter = mapper.writer();
  private static final ObjectReader genericReader = mapper.reader();

  public static Behavior<AriCommandMessage> create(
      final CommandResponseHandler commandResponseHandler,
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final Sink<ProducerRecord<String, String>, CompletionStage<Done>> producerFlow) {
    return Behaviors.setup(
        context ->
            Behaviors.receive(AriCommandMessage.class)
                .onMessage(
                    AriCommandMessage.class,
                    msg -> {
                      executeCommand(
                          context.getSystem(),
                          commandResponseHandler,
                          callContextProvider,
                          producerFlow,
                          msg);
                      return Behaviors.same();
                    })
                .build());
  }

  private static void executeCommand(
      final ActorSystem<?> system,
      final CommandResponseHandler commandResponseHandler,
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final Sink<ProducerRecord<String, String>, CompletionStage<Done>> producerSink,
      final AriCommandMessage msg) {

    final Config serviceConfig = ConfigFactory.load().getConfig(SERVICE);

    final Config kafkaConfig = serviceConfig.getConfig(KAFKA);
    final String commandsTopic = kafkaConfig.getString(COMMANDS_TOPIC);
    final String eventsAndResponsesTopic = kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC);

    final Config restConfig = serviceConfig.getConfig(REST);
    final String restUri = restConfig.getString(URI);
    final String restUser = restConfig.getString(USER);
    final String restPassword = restConfig.getString(PASSWORD);

    LOGGER.info(">>> ARI COMMAND: {}", msg.getCommandJson());
    try {
      final AriCommandEnvelope msgEnvelope = reader.readValue(msg.getCommandJson());
      AriCommandResponseProcessing.registerCallContext(
              callContextProvider, msgEnvelope.getCallContext(), msgEnvelope.getAriCommand())
          .get();
      final Source<ProducerRecord<String, String>, NotUsed> messageNotUsedSource =
          Source.single(
                  new CallContextAndCommandRequestContext(
                      msgEnvelope.getCallContext(),
                      msgEnvelope.getCommandId(),
                      msgEnvelope.getAriCommand()))
              .map(
                  context ->
                      Tuple.of(
                          toHttpRequest(context.getAriCommand(), restUri, restUser, restPassword),
                          context))
              .mapAsync(
                  1,
                  requestAndContext -> {
                    final Instant start = Instant.now();
                    return commandResponseHandler
                        .apply(requestAndContext)
                        .handle(
                            (response, error) -> {
                              Metrics.recordAriCommandRequest(
                                  requestAndContext._2().getAriCommand(),
                                  Duration.between(start, Instant.now()),
                                  error != null);
                              return Tuple.of(
                                  handleErrorInHTTPResponse(response, error),
                                  requestAndContext._2());
                            });
                  })
              .mapAsync(
                  1,
                  rawHttpResponseAndContext -> {
                    msg.getReplyTo().tell(StatusReply.success(null));
                    return toAriResponse(rawHttpResponseAndContext, system);
                  })
              .map(
                  ariResponseAndContext ->
                      envelopeAriResponseToProducerRecord(
                          commandsTopic, eventsAndResponsesTopic, ariResponseAndContext))
              .log(">>>   ARI RESPONSE", ProducerRecord::value)
              .withAttributes(LOG_LEVELS);

      messageNotUsedSource.to(producerSink).run(system);
    } catch (Exception e) {
      system.log().error("Unable to process command. {}", msg.getCommandJson(), e);
      msg.getReplyTo().tell(StatusReply.error(e));
    }
  }

  //    public static RunnableGraph<Supplier<Consumer.DrainingControl<Done>>>
  // commandResponseProcessing(
  //      final ActorSystem<?> system,
  //      final CommandResponseHandler commandResponseHandler,
  //      final ActorRef<CallContextProviderMessage> callContextProvider,
  //      final Source<CommittableMessage<String, String>, Supplier<Control>> source,
  //      final Flow<
  //              Envelope<String, String, CommittableOffset>,
  //              Results<String, String, CommittableOffset>,
  //              NotUsed>
  //          producerFlow,
  //      final Sink<CommittableOffset, CompletionStage<Done>> sink) {
  //    final Function<Throwable, Directive> decider =
  //        error -> {
  //          system.log().error("Error in some stage; restarting stream ...", error);
  //          Metrics.countCommandResponseProcessorRestarts();
  //          return (Directive) Supervision.restart();
  //        };
  //
  //    final Config serviceConfig = ConfigFactory.load().getConfig(SERVICE);
  //    final String stasisApp = serviceConfig.getString(STASIS_APP);
  //
  //    final Config kafkaConfig = serviceConfig.getConfig(KAFKA);
  //    final String commandsTopic = kafkaConfig.getString(COMMANDS_TOPIC);
  //    final String eventsAndResponsesTopic = kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC);
  //
  //    final Config restConfig = serviceConfig.getConfig(REST);
  //    final String restUri = restConfig.getString(URI);
  //    final String restUser = restConfig.getString(USER);
  //    final String restPassword = restConfig.getString(PASSWORD);
  //
  //    return source
  //        .log(
  //            ">>>   ARI COMMAND",
  //            (CommittableMessage<String, String> message) -> message.record().value())
  //        .withAttributes(LOG_LEVELS)
  //        .flatMapConcat(
  //                committableMessage -> {
  //                    return executeCommand(system, commandResponseHandler, callContextProvider,
  // producerFlow, commandsTopic, eventsAndResponsesTopic, restUri, restUser, restPassword,
  // committableMessage);
  //                })
  //        .toMat(
  //            sink,
  //            (control, streamCompletion) ->
  //                (Supplier<Consumer.DrainingControl<Done>>)
  //                    () -> Consumer.createDrainingControl(control.get(), streamCompletion))
  //        .withAttributes(ActorAttributes.withSupervisionStrategy(decider));
  //  }

  private static HttpResponse handleErrorInHTTPResponse(HttpResponse response, Throwable error) {
    return error != null ? HttpResponse.create().withStatus(500) : response;
  }

  private static ProducerRecord<String, String> envelopeAriResponseToProducerRecord(
      final String commandsTopic,
      final String eventsAndResponsesTopic,
      final Tuple2<AriResponse, CallContextAndCommandRequestContext> ariResponseAndContext) {
    final AriResponse ariResponse = ariResponseAndContext._1;
    final CallContextAndCommandRequestContext context = ariResponseAndContext._2;

    AriMessageEnvelope ariMessageEnvelope =
        envelopeAriResponse(ariResponse, context, commandsTopic);

    return new ProducerRecord<>(
        eventsAndResponsesTopic,
        context.getCallContext(),
        marshallAriMessageEnvelope(ariMessageEnvelope));
  }

  private static AriMessageEnvelope envelopeAriResponse(
      AriResponse ariResponse,
      CallContextAndCommandRequestContext context,
      String kafkaCommandsTopic) {
    final AriCommand command = context.getAriCommand();

    return new AriMessageEnvelope(
        AriMessageType.RESPONSE,
        kafkaCommandsTopic,
        ariResponse,
        context.getCallContext(),
        command.extractResourceRelations().map(AriResourceRelation::getResource).toJavaList(),
        context.getCommandId(),
        new CommandRequest(command.getMethod(), command.getUrl()));
  }

  private static String marshallAriMessageEnvelope(AriMessageEnvelope messageEnvelope) {
    return Try.of(() -> ariMessageEnvelopeWriter.writeValueAsString(messageEnvelope))
        .getOrElseThrow(t -> new RuntimeException("Failed to serialize AriResponse", t));
  }

  private static CompletionStage<Tuple2<AriResponse, CallContextAndCommandRequestContext>>
      toAriResponse(
          final Tuple2<HttpResponse, CallContextAndCommandRequestContext> responseWithContext,
          final ActorSystem<?> system) {
    final HttpResponse response = responseWithContext._1;

    final long contentLength =
        response
            .entity()
            .getContentLengthOption()
            .orElseThrow(() -> new RuntimeException("failed to get content length"));

    return response
        .entity()
        .toStrict(contentLength, system)
        .thenCompose(
            strictText ->
                Option.of(
                        StringUtils.trimToNull(
                            strictText.getData().decodeString(Charset.defaultCharset())))
                    .map(
                        rawBody ->
                            Try.of(() -> genericReader.readTree(rawBody))
                                .map(
                                    jsonBody ->
                                        new AriResponse(response.status().intValue(), jsonBody))
                                .map(res -> responseWithContext.map1(httpResponse -> res))
                                .map(tuple -> CompletableFuture.completedFuture(tuple))
                                .getOrElseGet(
                                    t ->
                                        Future
                                            .<Tuple2<
                                                    AriResponse,
                                                    CallContextAndCommandRequestContext>>
                                                failed(t)
                                            .toCompletableFuture()))
                    .getOrElse(
                        CompletableFuture.completedFuture(
                            responseWithContext.map1(
                                httpResponse ->
                                    new AriResponse(response.status().intValue(), null)))));
  }

  private static HttpRequest toHttpRequest(
      AriCommand ariCommand, String uri, String user, String password) {
    final String method = ariCommand.getMethod();

    return HttpMethods.lookup(method)
        .map(
            validHttpMethod -> {
              final HttpRequest httpRequest =
                  HttpRequest.create()
                      .withMethod(validHttpMethod)
                      .addCredentials(HttpCredentials.createBasicHttpCredentials(user, password))
                      .withUri(uri + ariCommand.getUrl());

              final Option<String> maybeBodyJson = extractBodyJson(ariCommand.getBody());
              if (maybeBodyJson.isDefined()) {
                return httpRequest.withEntity(
                    ContentTypes.APPLICATION_JSON, maybeBodyJson.get().getBytes());
              } else {
                return httpRequest;
              }
            })
        .orElseThrow(() -> new RuntimeException(String.format("Invalid http method: %s", method)));
  }

  private static Option<String> extractBodyJson(final JsonNode body) {
    if (!(body == null || body instanceof NullNode)) {
      try {
        return Option.some(genericWriter.writeValueAsString(body));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    } else {
      return Option.none();
    }
  }
}
