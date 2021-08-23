package io.retel.ariproxy.health;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.typesafe.config.Config;
import io.retel.ariproxy.akkajavainterop.PatternsAdapter;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.health.api.NewHealthRecipient;
import io.retel.ariproxy.health.api.ProvideHealthReport;
import io.retel.ariproxy.health.api.ProvideMonitoring;
import io.vavr.concurrent.Future;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringDeserializer;

public class KafkaConnectionCheck extends AbstractLoggingActor {

  public static final String ACTOR_NAME = "kafka-connection-check";

  static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";
  static final String COMMANDS_TOPIC = "commands-topic";
  static final String BOOTSTRAP_SERVERS = "bootstrap-servers";
  static final String CONSUMER_GROUP = "consumer-group";

  private final Config kafkaConfig;
  private final List<String> wantedTopics;

  public static Props props(Config kafkaConfig) {
    return Props.create(KafkaConnectionCheck.class, kafkaConfig);
  }

  private KafkaConnectionCheck(Config kafkaConfig) {
    this.kafkaConfig = kafkaConfig;
    this.wantedTopics =
        Arrays.asList(
            kafkaConfig.getString(COMMANDS_TOPIC),
            kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC));
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
        .match(ProvideHealthReport.class, this::provideHealthReportHandler)
        .match(NewHealthRecipient.class, ignored -> this.registerMonitoring())
        .matchAny(msg -> log().warning("Unexpected message received {}", msg))
        .build();
  }

  private void registerMonitoring() {
    getContext().getSystem().eventStream().publish(new ProvideMonitoring(ACTOR_NAME, self()));
  }

  @Override
  public void preStart() throws Exception {
    context().system().eventStream().subscribe(self(), NewHealthRecipient.class);
    registerMonitoring();
    super.preStart();
  }

  private void provideHealthReportHandler(ProvideHealthReport cmd) {
    final String bootstrapServers = kafkaConfig.getString(BOOTSTRAP_SERVERS);
    final String consumerGroup = kafkaConfig.getString(CONSUMER_GROUP) + "-check";

    PatternsAdapter.pipeTo(
        provideHealthReport(bootstrapServers, consumerGroup, wantedTopics),
        sender(),
        context().dispatcher());
  }

  private Future<HealthReport> provideHealthReport(
      final String bootstrapServers, final String consumerGroup, final List<String> neededTopics) {
    final Properties kafkaProperties = new Properties();
    kafkaProperties.setProperty(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
    kafkaProperties.setProperty(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getCanonicalName());
    kafkaProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
    kafkaProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    return Future.of(
        () -> {
          try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProperties)) {

            final Map<String, List<PartitionInfo>> receivedTopics =
                consumer.listTopics(Duration.ofMillis(100));

            final List<String> missingTopics =
                neededTopics.stream()
                    .filter(s -> !receivedTopics.containsKey(s))
                    .collect(Collectors.toList());
            if (!missingTopics.isEmpty()) {
              return HealthReport.error(
                  String.format(
                      "KafkaConnectionCheck: missing topics, please create: %s", missingTopics));
            }

            return HealthReport.ok();

          } catch (TimeoutException timeoutException) {
            return HealthReport.error(
                String.format(
                    "KafkaConnectionCheck: timeout during connection to servers: %s",
                    bootstrapServers));
          }
        });
  }
}
