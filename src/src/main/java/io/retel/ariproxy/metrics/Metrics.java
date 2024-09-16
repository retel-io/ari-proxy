package io.retel.ariproxy.metrics;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
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
import io.retel.ariproxy.health.api.HealthReport;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class Metrics {

  private static final Duration MAX_EXPECTED_DURATION = Duration.ofSeconds(10);
  private static final Config METRICS_CONFIG =
      ConfigFactory.load().getConfig("service").getConfig("metrics");
  private static final Config COMMON_TAGS_CONFIG = METRICS_CONFIG.getConfig("common-tags");
  private static final List<Tag> COMMON_TAGS =
      COMMON_TAGS_CONFIG.entrySet().stream()
          .map(entry -> Tag.of(entry.getKey(), entry.getValue().unwrapped().toString()))
          .toList();
  private static final Duration HEALTH_REPORT_TIMEOUT =
      METRICS_CONFIG.getDuration("healthReportTimeout");
  private static final String BACKING_SERVICE_AVAILABILITY_METRIC_NAME =
      METRICS_CONFIG.getConfig("measurement-names").getString("backing-service-availability");
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
    REGISTRY.config().commonTags(COMMON_TAGS);
    REGISTRY.add(prometheusRegistry);
    REGISTRY.add(jmxMeterRegistry);

    new JvmMemoryMetrics().bindTo(REGISTRY);
    new JvmGcMetrics().bindTo(REGISTRY);
    new JvmThreadMetrics().bindTo(REGISTRY);

    registerAriEventCounters();
  }

  public static void configureCallContextProviderAvailabilitySupplier(
      final Supplier<CompletableFuture<HealthReport>> callContextProviderAvailability,
      final String backingServiceName) {
    Gauge.builder(
            BACKING_SERVICE_AVAILABILITY_METRIC_NAME,
            mapHealthReportToGaugeValue(callContextProviderAvailability))
        .tags("backing_service", backingServiceName.toLowerCase())
        .register(REGISTRY);
  }

  public static void configureKafkaAvailabilitySupplier(
      final Supplier<CompletableFuture<HealthReport>> kafkaAvailability) {
    Gauge.builder(
            BACKING_SERVICE_AVAILABILITY_METRIC_NAME,
            mapHealthReportToGaugeValue(kafkaAvailability))
        .tags("backing_service", "kafka")
        .register(REGISTRY);
  }

  public static void configureAriAvailabilitySupplier(
      final Supplier<CompletableFuture<HealthReport>> ariAvailability) {
    Gauge.builder(
            BACKING_SERVICE_AVAILABILITY_METRIC_NAME, mapHealthReportToGaugeValue(ariAvailability))
        .tags("backing_service", "asterisk")
        .register(REGISTRY);
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

  private static Supplier<Number> mapHealthReportToGaugeValue(
      final Supplier<CompletableFuture<HealthReport>> healthReportSupplier) {
    return () -> {
      try {
        return healthReportSupplier
            .get()
            .thenApply(report -> report.errors().isEmpty() ? 1 : 0)
            .get(HEALTH_REPORT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        return 0;
      }
    };
  }
}
