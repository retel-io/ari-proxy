package io.retel.ariproxy.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommandType;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Metrics {

  // Metric Names
  private static final String OUTGOING_REQUESTS_METRIC_NAME = "ari-proxy.outgoing.requests";
  private static final String OUTGOING_REQUESTS_TIMER_METRIC_NAME =
      OUTGOING_REQUESTS_METRIC_NAME + ".duration";
  private static final String OUTGOING_REQUESTS_ERRORS_METRIC_NAME =
      OUTGOING_REQUESTS_METRIC_NAME + ".errors";

  // Registry
  private static final CompositeMeterRegistry REGISTRY = new CompositeMeterRegistry();
  private static final PrometheusMeterRegistry prometheusRegistry =
      new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

  private static final JmxMeterRegistry jmxMeterRegistry =
      new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);

  static {
    REGISTRY.add(prometheusRegistry);
    REGISTRY.add(jmxMeterRegistry);

    new JvmMemoryMetrics().bindTo(REGISTRY);
    new JvmGcMetrics().bindTo(REGISTRY);
    new JvmThreadMetrics().bindTo(REGISTRY);
  }

  private static Timer getTimerWithHistogram(
      final String metricName, final List<Tag> tags, Duration maxExpectedDuration) {
    return Timer.builder(metricName)
        .publishPercentileHistogram()
        .maximumExpectedValue(maxExpectedDuration)
        .tags(tags)
        .register(REGISTRY);
  }

  public static void recordAriCommandRequest(
      final AriCommand ariCommand, final Duration duration, final boolean isError) {
    final String method = ariCommand.getMethod();
    final String templatedPath =
        AriCommandType.extractTemplatedPath(ariCommand.getUrl()).orElse("UNKNOWN");

    final List<Tag> tags = List.of(Tag.of("method", method), Tag.of("path", templatedPath));

    REGISTRY.counter(OUTGOING_REQUESTS_METRIC_NAME, tags).increment();
    if (isError) {
      REGISTRY.counter(OUTGOING_REQUESTS_ERRORS_METRIC_NAME, tags).increment();
    }

    getTimerWithHistogram(OUTGOING_REQUESTS_TIMER_METRIC_NAME, tags, Duration.ofSeconds(10))
        .record(duration);
  }

  public static String scrapePrometheusRegistry() {
    return prometheusRegistry.scrape();
  }
}
