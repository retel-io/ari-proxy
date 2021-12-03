package io.retel.ariproxy.metrics;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.PreRestart;
import akka.actor.typed.javadsl.Behaviors;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import io.retel.ariproxy.metrics.api.PrometheusMetricsReport;
import io.retel.ariproxy.metrics.api.ReportPrometheusMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class MetricsService {

  private static final String METRIC_NAME_PERSISTENCE_UPDATE_DELAY =
      "ariproxy.persistence.WriteTime";
  private static final String METRIC_NAME_CALL_SETUP_DELAY = "ariproxy.calls.SetupDelay";

  private MetricsService() {
    throw new IllegalStateException("Utility class");
  }

  public static Behavior<MetricsServiceMessage> create() {
    return Behaviors.setup(
        ctx -> {
          final CompositeMeterRegistry registry = new CompositeMeterRegistry();
          final JmxMeterRegistry jmxMeterRegistry =
              new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);
          final PrometheusMeterRegistry prometheusMeterRegistry =
              new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

          registry.add(jmxMeterRegistry);
          registry.add(prometheusMeterRegistry);

          Timer.builder(METRIC_NAME_PERSISTENCE_UPDATE_DELAY)
              .publishPercentileHistogram(true)
              .maximumExpectedValue(Duration.ofSeconds(3))
              .register(registry);
          Timer.builder(METRIC_NAME_CALL_SETUP_DELAY)
              .publishPercentileHistogram(true)
              .maximumExpectedValue(Duration.ofSeconds(3))
              .register(registry);

          final Map<String, Instant> timers = new HashMap<>();
          final Map<String, Counter> counters = new HashMap<>();
          final Map<AriMessageType, Counter> eventCounters = new HashMap<>();

          return Behaviors.receive(MetricsServiceMessage.class)
              .onMessage(
                  PresistenceUpdateTimerStart.class,
                  msg -> handlePersistenceUpdateStart(timers, msg))
              .onMessage(
                  PersistenceUpdateTimerStop.class,
                  msg -> handlePersistenceUpdateStop(timers, registry, msg))
              .onMessage(
                  IncreaseCounter.class, msg -> handleIncreaseCounter(counters, registry, msg))
              .onMessage(
                  IncreaseAriEventCounter.class,
                  msg -> handleAriIncreaseCounter(eventCounters, registry, msg))
              .onMessage(StartCallSetupTimer.class, msg -> handleStartCallSetupTimer(timers, msg))
              .onMessage(
                  StopCallSetupTimer.class, msg -> handleStopCallSetupTimer(timers, registry, msg))
              .onMessage(
                  ReportPrometheusMetrics.class,
                  msg -> handleReportPrometheusMetrics(prometheusMeterRegistry, msg))
              .onSignal(PostStop.class, signal -> cleanup(registry))
              .onSignal(PreRestart.class, signal -> cleanup(registry))
              .build();
        });
  }

  private static Behavior<MetricsServiceMessage> handleReportPrometheusMetrics(
      final PrometheusMeterRegistry prometheusMeterRegistry, final ReportPrometheusMetrics msg) {
    msg.replyTo().tell(new PrometheusMetricsReport(prometheusMeterRegistry.scrape()));
    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handlePersistenceUpdateStart(
      final Map<String, Instant> timers, final PresistenceUpdateTimerStart message) {

    timers.put(message.getContext(), Instant.now());
    message.getReplyTo().ifPresent(replyTo -> replyTo.tell(MetricRegistered.TIMER_STARTED));

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handlePersistenceUpdateStop(
      final Map<String, Instant> timers,
      final MeterRegistry registry,
      final PersistenceUpdateTimerStop message) {
    final Instant timer = timers.get(message.getContext());
    if (timer != null) {
      registry
          .timer(METRIC_NAME_PERSISTENCE_UPDATE_DELAY)
          .record(Duration.between(timer, Instant.now()));
      timers.remove(message.getContext());
    }

    message.getReplyTo().ifPresent(replyTo -> replyTo.tell(MetricRegistered.TIMER_STOPPED));

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handleIncreaseCounter(
      final Map<String, Counter> counters,
      final MeterRegistry registry,
      final IncreaseCounter message) {
    counters.computeIfAbsent(message.getName(), registry::counter).increment();
    message.getReplyTo().ifPresent(replyTo -> replyTo.tell(MetricRegistered.COUNTER_INCREASED));

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handleAriIncreaseCounter(
      final Map<AriMessageType, Counter> counters,
      final MeterRegistry registry,
      final IncreaseAriEventCounter message) {
    counters
        .computeIfAbsent(
            message.getEventType(),
            eventType ->
                Counter.builder("ariproxy.events")
                    .tag("eventType", eventType.name())
                    .register(registry))
        .increment();
    message.getReplyTo().ifPresent(replyTo -> replyTo.tell(MetricRegistered.COUNTER_INCREASED));

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handleStartCallSetupTimer(
      final Map<String, Instant> timers, final StartCallSetupTimer message) {
    timers.put(message.getCallContext(), Instant.now());
    message.getReplyTo().ifPresent(replyTo -> replyTo.tell(MetricRegistered.TIMER_STARTED));

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handleStopCallSetupTimer(
      final Map<String, Instant> timers,
      final MeterRegistry registry,
      final StopCallSetupTimer message) {
    final Instant timer = timers.get(message.getCallcontext());
    if (timer != null) {
      registry.timer(METRIC_NAME_CALL_SETUP_DELAY).record(Duration.between(timer, Instant.now()));
      timers.remove(message.getCallcontext());
    }

    message.getReplyTo().ifPresent(replyTo -> replyTo.tell(MetricRegistered.TIMER_STOPPED));

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> cleanup(final MeterRegistry registry) {
    registry.close();

    return Behaviors.same();
  }
}
