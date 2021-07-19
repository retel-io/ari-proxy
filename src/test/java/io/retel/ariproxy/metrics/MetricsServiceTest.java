package io.retel.ariproxy.metrics;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {

  private static ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());
  private static final String CALL_CONTEXT = "theCallContext";
  private static final String APP_NAME = "theAppName";

  @Test
  void increaseCounterMessageIsRespondedProperly() {
    final ActorRef<MetricsServiceMessage> metricsService = testKit.spawn(MetricsService.create());
    final TestProbe<MetricRegistered> probe = testKit.createTestProbe();
    metricsService.tell(new IncreaseCounter(CALL_CONTEXT, probe.getRef()));

    probe.expectMessage(MetricRegistered.COUNTER_INCREASED);
  }

  @Test
  void callSetupTimerMessageIsRespondedProperly() {
    final ActorRef<MetricsServiceMessage> metricsService = testKit.spawn(MetricsService.create());
    final TestProbe<MetricRegistered> probe = testKit.createTestProbe();

    metricsService.tell(new StartCallSetupTimer(CALL_CONTEXT, probe.getRef()));
    probe.expectMessage(MetricRegistered.TIMER_STARTED);

    metricsService.tell(new StopCallSetupTimer(CALL_CONTEXT, APP_NAME, probe.getRef()));
    probe.expectMessage(MetricRegistered.TIMER_STOPPED);
  }

  @Test
  void redisUpdateTimerMessageIsRespondedProperly() {
    final ActorRef<MetricsServiceMessage> metricsService = testKit.spawn(MetricsService.create());
    final TestProbe<MetricRegistered> probe = testKit.createTestProbe();

    metricsService.tell(new RedisUpdateTimerStart("CALL CONTEXT", probe.getRef()));
    probe.expectMessage(MetricRegistered.TIMER_STARTED);

    metricsService.tell(new RedisUpdateTimerStop(CALL_CONTEXT, probe.getRef()));
    probe.expectMessage(MetricRegistered.TIMER_STOPPED);
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }
}
