package io.retel.ariproxy;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.CallContextProvider;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.callcontext.api.ReportHealth;
import io.retel.ariproxy.boundary.commandsandresponses.AriCommandResponseKafkaProcessor;
import io.retel.ariproxy.boundary.events.WebsocketMessageToProducerRecordTranslator;
import io.retel.ariproxy.health.HealthService;
import io.retel.ariproxy.health.KafkaConnectionCheck;
import io.retel.ariproxy.health.KafkaConnectionCheck.ReportKafkaConnectionHealth;
import io.retel.ariproxy.metrics.Metrics;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class Main {

  private static final String BOOTSTRAP_SERVERS = "bootstrap-servers";
  private static final String WEBSOCKET_URI = "websocket-uri";
  private static final String CONSUMER_GROUP = "consumer-group";
  private static final String COMMANDS_TOPIC = "commands-topic";
  private static final String SERVICE = "service";
  private static final String NAME = "name";
  private static final String HTTPPORT = "httpport";
  public static final String KAFKA = "kafka";
  private static final Duration HEALTH_REPORT_TIMEOUT = Duration.ofMillis(100);

  static {
    System.setProperty(
        "log4j.shutdownCallbackRegistry", "com.djdch.log4j.StaticShutdownCallbackRegistry");
  }

  public static void main(String[] args) {

    final Config serviceConfig = ConfigFactory.load().getConfig(SERVICE);

    ActorSystem.create(
        Behaviors.setup(
            ctx -> {
              final ActorRef<CallContextProviderMessage> callContextProvider =
                  ctx.spawn(CallContextProvider.create(), "call-context-provider");

              final ActorRef<ReportKafkaConnectionHealth> kafkaConnectionCheck =
                  ctx.spawn(
                      KafkaConnectionCheck.create(serviceConfig.getConfig(KAFKA)),
                      "kafka-connection-check");

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
                              .toCompletableFuture()),
                  Metrics::scrapePrometheusRegistry,
                  serviceConfig.getInt(HTTPPORT));

              runAriEventProcessor(
                  serviceConfig,
                  ctx.getSystem(),
                  callContextProvider,
                  () -> ctx.getSystem().terminate());

              runAriCommandResponseProcessor(
                  serviceConfig.getConfig(KAFKA), ctx.getSystem(), callContextProvider);

              return Behaviors.ignore();
            }),
        serviceConfig.getString(NAME));
  }

  private static void runAriCommandResponseProcessor(
      final Config kafkaConfig,
      final ActorSystem<Void> system,
      final ActorRef<CallContextProviderMessage> callContextProvider) {
    final ConsumerSettings<String, String> consumerSettings =
        ConsumerSettings.create(system, new StringDeserializer(), new StringDeserializer())
            .withBootstrapServers(kafkaConfig.getString(BOOTSTRAP_SERVERS))
            .withGroupId(kafkaConfig.getString(CONSUMER_GROUP))
            .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
            .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    final ProducerSettings<String, String> producerSettings =
        ProducerSettings.create(system, new StringSerializer(), new StringSerializer())
            .withBootstrapServers(kafkaConfig.getString(BOOTSTRAP_SERVERS));

    final Source<ConsumerRecord<String, String>, NotUsed> source =
        RestartSource.withBackoff(
            Duration.of(5, ChronoUnit.SECONDS),
            Duration.of(10, ChronoUnit.SECONDS),
            0.2,
            () ->
                Consumer.plainSource(
                        consumerSettings,
                        Subscriptions.topics(kafkaConfig.getString(COMMANDS_TOPIC)))
                    .mapMaterializedValue(control -> NotUsed.getInstance()));

    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Producer.plainSink(producerSettings).mapMaterializedValue(done -> NotUsed.getInstance());

    AriCommandResponseKafkaProcessor.commandResponseProcessing(
            system,
            requestAndContext -> Http.get(system).singleRequest(requestAndContext._1),
            callContextProvider,
            source,
            sink)
        .run(system);
  }

  private static void runAriEventProcessor(
      final Config serviceConfig,
      final ActorSystem<?> system,
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final Runnable applicationReplacedHandler) {
    // see:
    // https://doc.akka.io/docs/akka/2.5.8/java/stream/stream-error.html#delayed-restarts-with-a-backoff-stage
    final Flow<Message, Message, NotUsed> restartWebsocketFlow =
        RestartFlow.withBackoff(
            Duration.ofSeconds(3), // min backoff
            Duration.ofSeconds(30), // max backoff
            0.2, // adds 20% "noise" to vary the intervals slightly
            () -> createWebsocketFlow(system, serviceConfig.getString(WEBSOCKET_URI)));

    final Source<Message, NotUsed> source =
        Source.<Message>maybe().viaMat(restartWebsocketFlow, Keep.right());

    final ProducerSettings<String, String> producerSettings =
        ProducerSettings.create(system, new StringSerializer(), new StringSerializer())
            .withBootstrapServers(serviceConfig.getConfig(KAFKA).getString(BOOTSTRAP_SERVERS));

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

  // NOTE: We need this method because the resulting flow can only be materialized once;
  // see:
  // https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#websocketclientflow
  private static Flow<Message, Message, CompletionStage<WebSocketUpgradeResponse>>
      createWebsocketFlow(ActorSystem<?> system, String websocketUri) {
    return Http.get(system).webSocketClientFlow(WebSocketRequest.create(websocketUri));
  }
}
