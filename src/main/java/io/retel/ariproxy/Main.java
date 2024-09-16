package io.retel.ariproxy;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.Producer;
import akka.stream.RestartSettings;
import akka.stream.javadsl.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.CallContextProvider;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.callcontext.api.ReportHealth;
import io.retel.ariproxy.boundary.commandsandresponses.AriCommandResponseProcessor;
import io.retel.ariproxy.boundary.events.WebsocketMessageToProducerRecordTranslator;
import io.retel.ariproxy.health.AriConnectionCheck;
import io.retel.ariproxy.health.AriConnectionCheck.ReportAriConnectionHealth;
import io.retel.ariproxy.health.HealthService;
import io.retel.ariproxy.health.KafkaConnectionCheck;
import io.retel.ariproxy.health.KafkaConnectionCheck.ReportKafkaConnectionHealth;
import io.retel.ariproxy.metrics.Metrics;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final String BOOTSTRAP_SERVERS = "bootstrap-servers";
  private static final String WEBSOCKET_URI = "websocket-uri";
  private static final String SERVICE = "service";
  private static final String NAME = "name";
  private static final String HTTPPORT = "httpport";
  public static final String KAFKA = "kafka";
  public static final String KAFKA_SECURITY_PROTOCOL = "security.protocol";
  public static final String KAFKA_SECURITY_USER = "security.user";
  public static final String KAFKA_SECURITY_PASSWORD = "security.password";
  public static final String REST = "rest";
  private static final Duration HEALTH_REPORT_TIMEOUT = Duration.ofMillis(100);
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  static {
    System.setProperty(
        "log4j.shutdownCallbackRegistry", "com.djdch.log4j.StaticShutdownCallbackRegistry");
  }

  public static void main(String[] args) {
    LOGGER.info("Starting ari-proxy...");

    final Config serviceConfig = ConfigFactory.load().getConfig(SERVICE);
    LOGGER.info("Using config: {}", serviceConfig);

    ActorSystem.create(
        Behaviors.setup(
            ctx -> {
              final ActorRef<CallContextProviderMessage> callContextProvider =
                  ctx.spawn(CallContextProvider.create(), "call-context-provider");

              final ActorRef<ReportKafkaConnectionHealth> kafkaConnectionCheck =
                  ctx.spawn(
                      KafkaConnectionCheck.create(serviceConfig.getConfig(KAFKA)),
                      "kafka-connection-check");
              final ActorRef<ReportAriConnectionHealth> ariConnectionCheck =
                  ctx.spawn(
                      AriConnectionCheck.create(serviceConfig.getConfig(REST)),
                      "ari-connection-check");

              final ActorRef<AriCommandMessage> ariCommandResponseProcessor =
                  ctx.spawn(
                      AriCommandResponseProcessor.create(
                          requestAndContext ->
                              Http.get(ctx.getSystem()).singleRequest(requestAndContext._1),
                          callContextProvider,
                          createProducerSink(serviceConfig.getConfig(KAFKA), ctx.getSystem())),
                      "ari-command-response-processor");

              ctx.spawn(
                  KafkaConsumerActor.create(
                      serviceConfig.getConfig(KAFKA), ariCommandResponseProcessor),
                  "kafka-consumer");

              Metrics.configureCallContextProviderAvailabilitySupplier(
                  () ->
                      AskPattern.ask(
                              callContextProvider,
                              ReportHealth::new,
                              HEALTH_REPORT_TIMEOUT,
                              ctx.getSystem().scheduler())
                          .toCompletableFuture(),
                  getPersistenceStoreName(serviceConfig));
              Metrics.configureKafkaAvailabilitySupplier(
                  () ->
                      AskPattern.ask(
                              kafkaConnectionCheck,
                              ReportKafkaConnectionHealth::new,
                              HEALTH_REPORT_TIMEOUT,
                              ctx.getSystem().scheduler())
                          .toCompletableFuture());
              Metrics.configureAriAvailabilitySupplier(
                  () ->
                      AskPattern.ask(
                              ariConnectionCheck,
                              ReportAriConnectionHealth::new,
                              Duration.ofSeconds(2),
                              ctx.getSystem().scheduler())
                          .toCompletableFuture());

              HealthService.run(
                  ctx.getSystem(),
                  Arrays.asList(
                      () ->
                          AskPattern.ask(
                                  callContextProvider,
                                  ReportHealth::new,
                                  HEALTH_REPORT_TIMEOUT,
                                  ctx.getSystem().scheduler())
                              .toCompletableFuture(),
                      () ->
                          AskPattern.ask(
                                  kafkaConnectionCheck,
                                  ReportKafkaConnectionHealth::new,
                                  HEALTH_REPORT_TIMEOUT,
                                  ctx.getSystem().scheduler())
                              .toCompletableFuture(),
                      () ->
                          AskPattern.ask(
                                  ariConnectionCheck,
                                  ReportAriConnectionHealth::new,
                                  Duration.ofSeconds(2),
                                  ctx.getSystem().scheduler())
                              .toCompletableFuture()),
                  Metrics::scrapePrometheusRegistry,
                  serviceConfig.getInt(HTTPPORT));

              runAriEventProcessor(
                  serviceConfig,
                  ctx.getSystem(),
                  callContextProvider,
                  () -> ctx.getSystem().terminate());

              return Behaviors.ignore();
            }),
        serviceConfig.getString(NAME));

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOGGER.info("Received SIGTERM, shutting down...");
                }));
  }

  private static Sink<ProducerRecord<String, String>, CompletionStage<Done>> createProducerSink(
      final Config kafkaConfig, final ActorSystem<Void> system) {
    final ProducerSettings<String, String> producerSettings =
        getKafkaProducerSettings(kafkaConfig, system);

    return Producer.plainSink(
        producerSettings.withProducer(producerSettings.createKafkaProducer()));
  }

  private static void runAriEventProcessor(
      final Config serviceConfig,
      final ActorSystem<?> system,
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final Runnable applicationReplacedHandler) {
    // see:
    // https://doc.akka.io/docs/akka/2.5.8/java/stream/stream-error.html#delayed-restarts-with-a-backoff-stage
    final RestartSettings restartSettings =
        RestartSettings.create(Duration.ofSeconds(3), Duration.ofSeconds(30), 0.2);
    final Flow<Message, Message, NotUsed> restartWebsocketFlow =
        RestartFlow.withBackoff(
            restartSettings,
            () -> createWebsocketFlow(system, serviceConfig.getString(WEBSOCKET_URI)));

    final Source<Message, NotUsed> source =
        Source.<Message>maybe().viaMat(restartWebsocketFlow, Keep.right());

    final ProducerSettings<String, String> producerSettings =
        getKafkaProducerSettings(serviceConfig.getConfig(KAFKA), system);

    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Producer.plainSink(producerSettings).mapMaterializedValue(done -> NotUsed.getInstance());

    final RunnableGraph<NotUsed> processingPipeline =
        WebsocketMessageToProducerRecordTranslator.eventProcessing(
            system, callContextProvider, source, sink, applicationReplacedHandler);

    try {
      processingPipeline.run(system);
      system.log().debug("Successfully started ari event processor.");
    } catch (Exception e) {
      system.log().error("Failed to start ari event processor: ", e);
      System.exit(-1);
    }
  }

  private static ProducerSettings<String, String> getKafkaProducerSettings(
      final Config kafkaConfig, final ActorSystem<?> system) {
    ProducerSettings<String, String> producerSettings =
        ProducerSettings.create(system, new StringSerializer(), new StringSerializer())
            .withBootstrapServers(kafkaConfig.getString(BOOTSTRAP_SERVERS));

    if ("SASL_SSL".equals(kafkaConfig.getString(KAFKA_SECURITY_PROTOCOL))) {
      producerSettings =
          producerSettings
              .withProperty(
                  CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_SSL.name())
              .withProperty(
                  SaslConfigs.SASL_MECHANISM, ScramMechanism.SCRAM_SHA_256.mechanismName())
              .withProperty(
                  SaslConfigs.SASL_JAAS_CONFIG,
                  "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";"
                      .formatted(
                          kafkaConfig.getString(KAFKA_SECURITY_USER),
                          kafkaConfig.getString(KAFKA_SECURITY_PASSWORD)));
    }

    return producerSettings;
  }

  // NOTE: We need this method because the resulting flow can only be materialized once;
  // see:
  // https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#websocketclientflow
  private static Flow<Message, Message, CompletionStage<WebSocketUpgradeResponse>>
      createWebsocketFlow(ActorSystem<?> system, String websocketUri) {
    return Http.get(system).webSocketClientFlow(WebSocketRequest.create(websocketUri));
  }

  private static String getPersistenceStoreName(final Config serviceConfig) {
    final String persistenceStoreClassName =
        serviceConfig.hasPath("persistence-store")
            ? serviceConfig.getString("persistence-store")
            : "io.retel.ariproxy.persistence.plugin.RedisPersistenceStore";
    return Try.of(() -> Class.forName(persistenceStoreClassName))
        .flatMap(clazz -> Try.of(() -> clazz.getMethod("getName")))
        .flatMap(method -> Try.of(() -> (String) method.invoke(null)))
        .getOrElse("persistenceStore");
  }
}
