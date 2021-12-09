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
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResourceType;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public final class Metrics {

  private static final Duration MAX_EXPECTED_DURATION = Duration.ofSeconds(10);

  // Metric Names
  private static final String OUTGOING_REQUESTS_METRIC_NAME = "ari-proxy.outgoing.requests";
  private static final String OUTGOING_REQUESTS_TIMER_METRIC_NAME =
      "ari-proxy.outgoing.requests.duration";
  private static final String OUTGOING_REQUESTS_ERRORS_METRIC_NAME =
      "ari-proxy.outgoing.requests.errors";
  private static final String PROCESSOR_RESTARTS_METRIC_NAME = "ari-proxy.processor.restarts";
  private static final String EVENTS_METRIC_NAME = "ari-proxy.events";

  // Registry
  private static final CompositeMeterRegistry REGISTRY = new CompositeMeterRegistry();
  private static final PrometheusMeterRegistry prometheusRegistry =
      new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

  private static final JmxMeterRegistry jmxMeterRegistry =
      new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);

  private static final Counter CACHE_READ_ATTEMPTS_COUNTER =
      REGISTRY.counter("ari-proxy.cache.read.attempts");
  private static final Counter CACHE_READ_MISSES_COUNTER =
      REGISTRY.counter("ari-proxy.cache.read.misses");
  private static final Counter CACHE_READ_ERRORS_COUNTER =
      REGISTRY.counter("ari-proxy.cache.read.errors");

  private static final Counter PERSISTENCE_READ_ERRORS_COUNTERS =
      REGISTRY.counter("ari-proxy.persistence.read.errors");
  private static final Timer PERSISTENCE_WRITE_DURATION_TIMER =
      getTimerWithHistogram(
          "ari-proxy.persistence.write.duration", Collections.emptyList(), MAX_EXPECTED_DURATION);

  private static final Counter COMMAND_RESPONSE_PROCESSOR_RESTARTS_COUNTER =
      REGISTRY.counter(
          PROCESSOR_RESTARTS_METRIC_NAME, List.of(Tag.of("processorType", "commandResponse")));
  private static final Counter EVENT_PROCESSOR_RESTARTS_COUNTER =
      REGISTRY.counter(PROCESSOR_RESTARTS_METRIC_NAME, List.of(Tag.of("processorType", "event")));

  static {
    REGISTRY.add(prometheusRegistry);
    REGISTRY.add(jmxMeterRegistry);

    new JvmMemoryMetrics().bindTo(REGISTRY);
    new JvmGcMetrics().bindTo(REGISTRY);
    new JvmThreadMetrics().bindTo(REGISTRY);

    registerAriEventCounters();
  }

  private static void registerAriEventCounters() {
    for (final AriMessageType ariMessageType : AriMessageType.values()) {
      getAriEventCounter(ariMessageType);
    }
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

    getTimerWithHistogram(OUTGOING_REQUESTS_TIMER_METRIC_NAME, tags, MAX_EXPECTED_DURATION)
        .record(duration);
  }

  public static void countCacheReadAttempt() {
    CACHE_READ_ATTEMPTS_COUNTER.increment();
  }

  public static void countCacheReadMiss() {
    CACHE_READ_MISSES_COUNTER.increment();
  }

  public static void countCacheReadError() {
    CACHE_READ_ERRORS_COUNTER.increment();
  }

  public static void countPersistenceReadError() {
    PERSISTENCE_READ_ERRORS_COUNTERS.increment();
  }

  public static void timePersistenceWriteDuration(final Duration duration) {
    PERSISTENCE_WRITE_DURATION_TIMER.record(duration);
  }

  public static void countCommandResponseProcessorRestarts() {
    COMMAND_RESPONSE_PROCESSOR_RESTARTS_COUNTER.increment();
  }

  public static void countEventProcessorRestart() {
    EVENT_PROCESSOR_RESTARTS_COUNTER.increment();
  }

  public static String scrapePrometheusRegistry() {
    return prometheusRegistry.scrape();
  }

  public static void countAriEvent(final AriMessageType eventType) {
    getAriEventCounter(eventType).increment();
  }

  private static Counter getAriEventCounter(final AriMessageType ariMessageType) {
    return REGISTRY.counter(
        EVENTS_METRIC_NAME,
        List.of(
            Tag.of("eventType", ariMessageType.name()),
            Tag.of(
                "resourceType",
                ariMessageType
                    .getResourceType()
                    .map(AriResourceType::toString)
                    .getOrElse("NONE"))));
  }
}
