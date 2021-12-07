package io.retel.ariproxy.metrics;

import static org.junit.jupiter.api.Assertions.*;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import io.retel.ariproxy.metrics.api.PrometheusMetricsReport;
import io.retel.ariproxy.metrics.api.ReportPrometheusMetrics;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {

  private static final ActorTestKit testKit =
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
  void getPrometheusMetricsResponsesProperly() {
    final ActorRef<MetricsServiceMessage> metricsService = testKit.spawn(MetricsService.create());
    final TestProbe<MetricRegistered> counterProbe = testKit.createTestProbe();
    final TestProbe<PrometheusMetricsReport> probe = testKit.createTestProbe();

    metricsService.tell(new ReportPrometheusMetrics(probe.getRef()));
    probe.expectMessage(new PrometheusMetricsReport(""));

    metricsService.tell(new IncreaseCounter(CALL_CONTEXT, counterProbe.getRef()));
    counterProbe.expectMessage(MetricRegistered.COUNTER_INCREASED);

    metricsService.tell(new ReportPrometheusMetrics(probe.getRef()));
    final List<PrometheusMetricsReport> reports = probe.receiveSeveralMessages(1);
    assertTrue(reports.get(0).getPrometheusString().contains("theCallContext_total 1.0"));
  }

  @Test
  void persistenceUpdateTimerMessageIsRespondedProperly() {
    final ActorRef<MetricsServiceMessage> metricsService = testKit.spawn(MetricsService.create());
    final TestProbe<MetricRegistered> probe = testKit.createTestProbe();

    metricsService.tell(new PresistenceUpdateTimerStart("CALL CONTEXT", probe.getRef()));
    probe.expectMessage(MetricRegistered.TIMER_STARTED);

    metricsService.tell(new PersistenceUpdateTimerStop(CALL_CONTEXT, probe.getRef()));
    probe.expectMessage(MetricRegistered.TIMER_STOPPED);
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }
}
