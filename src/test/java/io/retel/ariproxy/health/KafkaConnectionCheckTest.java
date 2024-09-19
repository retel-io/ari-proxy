package io.retel.ariproxy.health;

import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.impl.ConfigImpl;
import io.retel.ariproxy.health.KafkaConnectionCheck.ReportKafkaConnectionHealth;
import io.retel.ariproxy.health.api.HealthReport;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class KafkaConnectionCheckTest {

  private static final ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());

  private static final KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"));

  public static final String COMMANDS_TOPIC = "commands-topic";
  public static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";

  @BeforeAll
  public static void beforeAll() {
    kafkaContainer.start();
    try (var admin =
        AdminClient.create(
            Map.of(BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()))) {
      admin.createTopics(
          List.of(
              new NewTopic(COMMANDS_TOPIC, 1, (short) 1),
              new NewTopic(EVENTS_AND_RESPONSES_TOPIC, 1, (short) 1)));
    }
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  @Test
  void provideOkHealthReport() {
    final Config testConfig =
        ConfigImpl.emptyConfig("KafkaConnectionCheckTest")
            .withValue(
                KafkaConnectionCheck.BOOTSTRAP_SERVERS,
                ConfigValueFactory.fromAnyRef(kafkaContainer.getBootstrapServers()))
            .withValue(
                "security",
                ConfigValueFactory.fromAnyRef(
                    Map.of(
                        "protocol", "PLAIN",
                        "user", "",
                        "password", "")))
            .withValue(
                KafkaConnectionCheck.CONSUMER_GROUP,
                ConfigValueFactory.fromAnyRef("my-test-consumer-group"))
            .withValue(
                KafkaConnectionCheck.COMMANDS_TOPIC, ConfigValueFactory.fromAnyRef(COMMANDS_TOPIC))
            .withValue(
                KafkaConnectionCheck.EVENTS_AND_RESPONSES_TOPIC,
                ConfigValueFactory.fromAnyRef(EVENTS_AND_RESPONSES_TOPIC));
    final TestProbe<HealthReport> healthReportProbe = testKit.createTestProbe();
    final akka.actor.typed.ActorRef<ReportKafkaConnectionHealth> connectionCheck =
        testKit.spawn(KafkaConnectionCheck.create(testConfig));

    connectionCheck.tell(new ReportKafkaConnectionHealth(healthReportProbe.ref()));

    healthReportProbe.expectMessage(HealthReport.ok());
  }

  @Test
  void provideNotOkHealthReport() {
    final Config testConfig =
        ConfigImpl.emptyConfig("KafkaConnectionCheckTest")
            .withValue(
                KafkaConnectionCheck.BOOTSTRAP_SERVERS,
                ConfigValueFactory.fromAnyRef(kafkaContainer.getBootstrapServers()))
            .withValue(
                "security",
                ConfigValueFactory.fromAnyRef(
                    Map.of(
                        "protocol", "PLAIN",
                        "user", "",
                        "password", "")))
            .withValue(
                KafkaConnectionCheck.CONSUMER_GROUP,
                ConfigValueFactory.fromAnyRef("my-test-consumer-group"))
            .withValue(
                KafkaConnectionCheck.COMMANDS_TOPIC,
                ConfigValueFactory.fromAnyRef("some-non-existing-topic"))
            .withValue(
                KafkaConnectionCheck.EVENTS_AND_RESPONSES_TOPIC,
                ConfigValueFactory.fromAnyRef(EVENTS_AND_RESPONSES_TOPIC));

    final TestProbe<HealthReport> healthReportProbe = testKit.createTestProbe();
    final akka.actor.typed.ActorRef<ReportKafkaConnectionHealth> connectionCheck =
        testKit.spawn(KafkaConnectionCheck.create(testConfig));

    connectionCheck.tell(new ReportKafkaConnectionHealth(healthReportProbe.ref()));

    final HealthReport report = healthReportProbe.expectMessageClass(HealthReport.class);
    assertThat(report.errors().size(), is(1));
  }
}
