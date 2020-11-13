package io.retel.ariproxy.boundary.commandsandresponses;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.japi.function.Function;
import akka.japi.function.Procedure;
import akka.stream.*;
import akka.stream.Supervision.Directive;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.*;
import io.retel.ariproxy.metrics.StopCallSetupTimer;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public class AriCommandResponseKafkaProcessor {

  private static final Attributes LOG_LEVELS =
      Attributes.createLogLevels(Logging.InfoLevel(), Logging.InfoLevel(), Logging.ErrorLevel());

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String SERVICE = "service";
  public static final String REST = "rest";
  public static final String URI = "uri";
  public static final String PASSWORD = "password";
  public static final String USER = "user";
  public static final String STASIS_APP = "stasis-app";
  public static final String KAFKA = "kafka";
  public static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";
  public static final String COMMANDS_TOPIC = "commands-topic";

  static {
    mapper.setSerializationInclusion(Include.NON_NULL);
  }

  private static final ObjectReader reader = mapper.readerFor(AriCommandEnvelope.class);
  private static final ObjectWriter ariMessageEnvelopeWriter =
      mapper.writerFor(AriMessageEnvelope.class);
  private static final ObjectWriter genericWriter = mapper.writer();
  private static final ObjectReader genericReader = mapper.reader();

  public static RunnableGraph<NotUsed> commandResponseProcessing(
      final ActorSystem system,
      final CommandResponseHandler commandResponseHandler,
      final ActorRef callContextProvider,
      final ActorRef metricsService,
      final Source<ConsumerRecord<String, String>, NotUsed> source,
      final Sink<ProducerRecord<String, String>, NotUsed> sink) {
    final Config serviceConfig = ConfigFactory.load().getConfig(SERVICE);
    final String stasisApp = serviceConfig.getString(STASIS_APP);

    final Config kafkaConfig = serviceConfig.getConfig(KAFKA);
    final String commandsTopic = kafkaConfig.getString(COMMANDS_TOPIC);
    final String eventsAndResponsesTopic = kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC);

    final Config restConfig = serviceConfig.getConfig(REST);
    final String restUri = restConfig.getString(URI);
    final String restUser = restConfig.getString(USER);
    final String restPassword = restConfig.getString(PASSWORD);

    final Function<Throwable, Supervision.Directive> decider =
        error -> {
          system.log().error(error, "Error in some stage; restarting stream ...");
          return (Directive) Supervision.restart();
        };

    return source
        .log(">>>   ARI COMMAND", ConsumerRecord::value)
        .withAttributes(LOG_LEVELS)
        .map(AriCommandResponseKafkaProcessor::unmarshallAriCommandEnvelope)
        .map(
            msgEnvelope -> {
              AriCommandResponseProcessing.registerCallContext(
                      callContextProvider,
                      msgEnvelope.getCallContext(),
                      msgEnvelope.getAriCommand())
                  .getOrElseThrow(t -> t)
                  .run();
              return new CallContextAndCommandRequestContext(
                  msgEnvelope.getCallContext(),
                  msgEnvelope.getCommandId(),
                  msgEnvelope.getAriCommand());
            })
        .map(
            context ->
                Tuple.of(
                    toHttpRequest(context.getAriCommand(), restUri, restUser, restPassword),
                    context))
        .mapAsync(
            1,
            requestAndContext ->
                commandResponseHandler
                    .apply(requestAndContext)
                    .thenApply(response -> Tuple.of(response, requestAndContext._2)))
        .wireTap(Sink.foreach(gatherMetrics(metricsService, stasisApp)))
        .mapAsync(1, rawHttpResponseAndContext -> toAriResponse(rawHttpResponseAndContext, system))
        .map(
            ariResponseAndContext ->
                envelopeAriResponseToProducerRecord(
                    commandsTopic, eventsAndResponsesTopic, ariResponseAndContext))
        .log(">>>   ARI RESPONSE", ProducerRecord::value)
        .withAttributes(LOG_LEVELS)
        .toMat(sink, Keep.none())
        .withAttributes(ActorAttributes.withSupervisionStrategy(decider));
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

  private static Procedure<Tuple2<HttpResponse, CallContextAndCommandRequestContext>> gatherMetrics(
      ActorRef metricsService, String applicationName) {
    return rawHttpResponseAndContext ->
        metricsService.tell(
            new StopCallSetupTimer(rawHttpResponseAndContext._2.getCallContext(), applicationName),
            ActorRef.noSender());
  }

  private static AriCommandEnvelope unmarshallAriCommandEnvelope(
      final ConsumerRecord<String, String> record) {
    return Try.of(() -> (AriCommandEnvelope) reader.readValue(record.value()))
        .getOrElseThrow(t -> new RuntimeException(t));
  }

  private static AriMessageEnvelope envelopeAriResponse(
      AriResponse ariResponse,
      CallContextAndCommandRequestContext context,
      String kafkaCommandsTopic) {
    return new AriMessageEnvelope(
        AriMessageType.RESPONSE,
        kafkaCommandsTopic,
        ariResponse,
        context.getCallContext(),
        context.getCommandId(),
        new CommandRequest(context.getAriCommand().getMethod(), context.getAriCommand().getUrl()));
  }

  private static String marshallAriMessageEnvelope(AriMessageEnvelope messageEnvelope) {
    return Try.of(() -> ariMessageEnvelopeWriter.writeValueAsString(messageEnvelope))
        .getOrElseThrow(t -> new RuntimeException("Failed to serialize AriResponse", t));
  }

  private static CompletionStage<Tuple2<AriResponse, CallContextAndCommandRequestContext>>
      toAriResponse(
          Tuple2<HttpResponse, CallContextAndCommandRequestContext> responseWithContext,
          ActorSystem system) {

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
    final JsonNode body = ariCommand.getBody();

    final String bodyJson =
        Option.of(body)
            .map(value -> Try.of(() -> genericWriter.writeValueAsString(value)))
            .getOrElse(Try.success(""))
            .getOrElseThrow(t -> new RuntimeException(t));

    return HttpMethods.lookup(method)
        .map(
            validHttpMethod ->
                HttpRequest.create()
                    .withMethod(validHttpMethod)
                    .addCredentials(HttpCredentials.createBasicHttpCredentials(user, password))
                    .withUri(uri + ariCommand.getUrl())
                    .withEntity(ContentTypes.APPLICATION_JSON, bodyJson.getBytes()))
        .orElseThrow(() -> new RuntimeException(String.format("Invalid http method: %s", method)));
  }
}
