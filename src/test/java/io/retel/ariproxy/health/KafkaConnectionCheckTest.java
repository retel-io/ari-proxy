package io.retel.ariproxy.health;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.impl.ConfigImpl;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.health.api.ProvideHealthReport;
import io.retel.ariproxy.persistence.*;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaConnectionCheckTest {

  @RegisterExtension
  public static final SharedKafkaTestResource sharedKafkaTestResource =
      new SharedKafkaTestResource();

  public static final String COMMANDS_TOPIC = "commands-topic";
  public static final String EVENTS_AND_RESPONSES_TOPIC = "events-and-responses-topic";

  private final String TEST_SYSTEM = this.getClass().getSimpleName();
  private ActorSystem system;

  @AfterEach
  void teardown() {
    TestKit.shutdownActorSystem(system);
    system.terminate();
  }

  @BeforeEach
  void setup() {
    system = ActorSystem.create(TEST_SYSTEM);
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
                KafkaConnectionCheck.CONSUMER_GROUP,
                ConfigValueFactory.fromAnyRef("my-test-consumer-group"))
            .withValue(
                KafkaConnectionCheck.COMMANDS_TOPIC, ConfigValueFactory.fromAnyRef(COMMANDS_TOPIC))
            .withValue(
                KafkaConnectionCheck.EVENTS_AND_RESPONSES_TOPIC,
                ConfigValueFactory.fromAnyRef(EVENTS_AND_RESPONSES_TOPIC));

    final TestKit probe = new TestKit(system);
    final ActorRef connectionCheck =
        system.actorOf(Props.create(KafkaConnectionCheck.class, testConfig));

    probe.send(connectionCheck, ProvideHealthReport.getInstance());

    final HealthReport report = probe.expectMsgClass(Duration.ofMillis(200), HealthReport.class);

    assertThat(report.errors().size(), is(0));
  }

  @Test
  void provideNotOkHealthReport() {

    final Config testConfig =
        ConfigImpl.emptyConfig("KafkaConnectionCheckTest")
            .withValue(
                KafkaConnectionCheck.BOOTSTRAP_SERVERS,
                ConfigValueFactory.fromAnyRef(sharedKafkaTestResource.getKafkaConnectString()))
            .withValue(
                KafkaConnectionCheck.CONSUMER_GROUP,
                ConfigValueFactory.fromAnyRef("my-test-consumer-group"))
            .withValue(
                KafkaConnectionCheck.COMMANDS_TOPIC,
                ConfigValueFactory.fromAnyRef("some-non-existing-topic"))
            .withValue(
                KafkaConnectionCheck.EVENTS_AND_RESPONSES_TOPIC,
                ConfigValueFactory.fromAnyRef(EVENTS_AND_RESPONSES_TOPIC));

    final TestKit probe = new TestKit(system);
    final ActorRef connectionCheck =
        system.actorOf(Props.create(KafkaConnectionCheck.class, testConfig));

    probe.send(connectionCheck, ProvideHealthReport.getInstance());

    final HealthReport report = probe.expectMsgClass(Duration.ofMillis(200), HealthReport.class);

    assertThat(report.errors().size(), is(1));
  }
}
