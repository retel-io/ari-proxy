package io.retel.ariproxy.health;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import io.retel.ariproxy.health.api.HealthReport;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringDeserializer;

public class KafkaConnectionCheck {

  static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";
  static final String COMMANDS_TOPIC = "commands-topic";
  static final String BOOTSTRAP_SERVERS = "bootstrap-servers";
  static final String CONSUMER_GROUP = "consumer-group";

  private KafkaConnectionCheck() {
    throw new IllegalStateException("Utility class");
  }

  public static Behavior<ReportKafkaConnectionHealth> create(final Config kafkaConfig) {
    final List<String> wantedTopics =
        Arrays.asList(
            kafkaConfig.getString(COMMANDS_TOPIC),
            kafkaConfig.getString(EVENTS_AND_RESPONSES_TOPIC));

    return Behaviors.receive(ReportKafkaConnectionHealth.class)
        .onMessage(
            ReportKafkaConnectionHealth.class,
            message -> reportHealth(kafkaConfig, wantedTopics, message))
        .build();
  }

  private static Behavior<ReportKafkaConnectionHealth> reportHealth(
      final Config kafkaConfig,
      final List<String> wantedTopics,
      final ReportKafkaConnectionHealth message) {
    provideHealthReport(
            kafkaConfig.getString(BOOTSTRAP_SERVERS),
            kafkaConfig.getString(CONSUMER_GROUP),
            wantedTopics)
        .thenAccept(healthReport -> message.replyTo().tell(healthReport));

    return Behaviors.same();
  }

  private static CompletableFuture<HealthReport> provideHealthReport(
      final String bootstrapServers, final String consumerGroup, final List<String> neededTopics) {
    final Properties kafkaProperties = new Properties();
    kafkaProperties.setProperty(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
    kafkaProperties.setProperty(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getCanonicalName());
    kafkaProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
    kafkaProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    return CompletableFuture.supplyAsync(
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

  public static class ReportKafkaConnectionHealth {
    final ActorRef<HealthReport> replyTo;

    public ReportKafkaConnectionHealth(final ActorRef<HealthReport> replyTo) {
      this.replyTo = replyTo;
    }

    public ActorRef<HealthReport> replyTo() {
      return replyTo;
    }

    @Override
    public String toString() {
      return ReflectionToStringBuilder.toString(this, SHORT_PREFIX_STYLE);
    }
  }
}