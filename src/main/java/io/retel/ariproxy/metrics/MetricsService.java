package io.retel.ariproxy.metrics;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.PreRestart;
import akka.actor.typed.javadsl.Behaviors;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import java.util.HashMap;
import java.util.Map;

public class MetricsService {

  private static final String METRIC_NAME_REDIS_UPDATE_DELAY = "RedisUpdateDelay";
  private static final String METRIC_NAME_CALL_SETUP_DELAY = "CallSetupDelay";

  private MetricsService() {
    throw new IllegalStateException("Utility class");
  }

  public static Behavior<MetricsServiceMessage> create() {
    return Behaviors.setup(
        ctx -> {
          final MeterRegistry registry = new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);
          final Map<String, Timer.Sample> timers = new HashMap<>();
          final Map<String, Counter> counters = new HashMap<>();

          return Behaviors.receive(MetricsServiceMessage.class)
              .onMessage(
                  RedisUpdateTimerStart.class, msg -> handleRedisUpdateStart(timers, registry, msg))
              .onMessage(
                  RedisUpdateTimerStop.class, msg -> handleRedisUpdateStop(timers, registry, msg))
              .onMessage(
                  IncreaseCounter.class, msg -> handleIncreaseCounter(counters, registry, msg))
              .onMessage(
                  StartCallSetupTimer.class,
                  msg -> handleStartCallSetupTimer(timers, registry, msg))
              .onMessage(
                  StopCallSetupTimer.class, msg -> handleStopCallSetupTimer(timers, registry, msg))
              .onSignal(PostStop.class, signal -> cleanup(registry))
              .onSignal(PreRestart.class, signal -> cleanup(registry))
              .build();
        });
  }

  private static Behavior<MetricsServiceMessage> handleRedisUpdateStart(
      final Map<String, Timer.Sample> timers,
      final MeterRegistry registry,
      final RedisUpdateTimerStart message) {
    timers.put(message.getContext(), Timer.start(registry));
    message.getReplyTo().ifPresent(replyTo -> replyTo.tell(MetricRegistered.TIMER_STARTED));

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handleRedisUpdateStop(
      final Map<String, Timer.Sample> timers,
      final MeterRegistry registry,
      final RedisUpdateTimerStop message) {
    final Timer.Sample timer = timers.get(message.getContext());
    if (timer != null) {
      timer.stop(registry.timer(METRIC_NAME_REDIS_UPDATE_DELAY));
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

  private static Behavior<MetricsServiceMessage> handleStartCallSetupTimer(
      final Map<String, Timer.Sample> timers,
      final MeterRegistry registry,
      final StartCallSetupTimer message) {
    timers.put(message.getCallContext(), Timer.start(registry));
    message.getReplyTo().tell(MetricRegistered.TIMER_STARTED);

    return Behaviors.same();
  }

  private static Behavior<MetricsServiceMessage> handleStopCallSetupTimer(
      final Map<String, Timer.Sample> timers,
      final MeterRegistry registry,
      final StopCallSetupTimer message) {
    final Timer.Sample timer = timers.get(message.getCallcontext());
    if (timer != null) {
      timer.stop(
          registry.timer(METRIC_NAME_CALL_SETUP_DELAY, "stasisApp", message.getApplication()));
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
