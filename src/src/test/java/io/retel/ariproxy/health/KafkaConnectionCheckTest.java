package io.retel.ariproxy.health;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.impl.ConfigImpl;
import io.retel.ariproxy.health.KafkaConnectionCheck.ReportKafkaConnectionHealth;
import io.retel.ariproxy.health.api.HealthReport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

class KafkaConnectionCheckTest {

  private static final ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());

  @RegisterExtension
  public static final SharedKafkaTestResource sharedKafkaTestResource =
      new SharedKafkaTestResource();

  public static final String COMMANDS_TOPIC = "commands-topic";
  public static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  @BeforeEach
  void setup() {
    sharedKafkaTestResource.getKafkaTestUtils().createTopic(COMMANDS_TOPIC, 1, (short) 1);
    sharedKafkaTestResource
        .getKafkaTestUtils()
        .createTopic(EVENTS_AND_RESPONSES_TOPIC, 1, (short) 1);
  }

  @Test
  void provideOkHealthReport() {
    final Config testConfig =
        ConfigImpl.emptyConfig("KafkaConnectionCheckTest")
            .withValue(
                KafkaConnectionCheck.BOOTSTRAP_SERVERS,
                ConfigValueFactory.fromAnyRef(sharedKafkaTestResource.getKafkaConnectString()))
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
                ConfigValueFactory.fromAnyRef(sharedKafkaTestResource.getKafkaConnectString()))
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
