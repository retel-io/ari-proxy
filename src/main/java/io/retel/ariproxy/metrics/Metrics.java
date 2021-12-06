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
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommandType;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Metrics {

  private static final Logger LOGGER = LoggerFactory.getLogger(Metrics.class);

  // Metric Names
  private static final String INCOMING_MESSAGES_METRIC_NAME = "ari-proxy.incoming.messages";
  private static final String INCOMING_MESSAGES_ERRORS_METRIC_NAME =
      "ari-proxy.incoming.messages.errors";
  public static final String INCOMING_MESSAGES_DURATION_METRIC_NAME =
      "ari-proxy.incoming.messages.duration";

  // Registry
  private static final CompositeMeterRegistry REGISTRY = new CompositeMeterRegistry();
  private static final PrometheusMeterRegistry prometheusRegistry =
      new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

  private static final JmxMeterRegistry jmxMeterRegistry =
      new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);

  // Tags
  private static final Tag COMMAND_TYPE_UNKOWN_TAG = Tag.of("command-type", "unknown");
  private static final Tag COMMAND_TYPE_BRIDGE_CREATION = Tag.of("command-type", "bridgeCreation");
  private static final Tag COMMAND_TYPE_BRIDGE = Tag.of("command-type", "bridge");
  private static final Tag COMMAND_TYPE_CHANNEL_CREATION =
      Tag.of("command-type", "channelCreation");
  private static final Tag COMMAND_TYPE_CHANNEL = Tag.of("command-type", "channel");
  private static final Tag COMMAND_TYPE_PLAYBACK_CREATION =
      Tag.of("command-type", "playbackCreation");
  private static final Tag COMMAND_TYPE_PLAYBACK = Tag.of("command-type", "playback");
  private static final Tag COMMAND_TYPE_RECORDING_CREATION =
      Tag.of("command-type", "recordingCreation");
  private static final Tag COMMAND_TYPE_RECORDING = Tag.of("command-type", "recording");
  private static final Tag COMMAND_TYPE_SNOOPING_CREATION =
      Tag.of("command-type", "snoopingCreation");

  // Counter
  private static final Counter COMMAND_TYPE_UNKOWN_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_UNKOWN_TAG));
  private static final Counter COMMAND_TYPE_BRIDGE_CREATION_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_BRIDGE_CREATION));
  private static final Counter COMMAND_TYPE_BRIDGE_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_BRIDGE));
  private static final Counter COMMAND_TYPE_CHANNEL_CREATION_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_CHANNEL_CREATION));
  private static final Counter COMMAND_TYPE_CHANNEL_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_CHANNEL));
  private static final Counter COMMAND_TYPE_PLAYBACK_CREATION_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_PLAYBACK_CREATION));
  private static final Counter COMMAND_TYPE_PLAYBACK_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_PLAYBACK));
  private static final Counter COMMAND_TYPE_RECORDING_CREATION_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_RECORDING_CREATION));
  private static final Counter COMMAND_TYPE_RECORDING_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_RECORDING));
  private static final Counter COMMAND_TYPE_SNOOPING_CREATION_INCOMING_MESSAGES_COUNTER =
      REGISTRY.counter(INCOMING_MESSAGES_METRIC_NAME, List.of(COMMAND_TYPE_SNOOPING_CREATION));

  static {
    REGISTRY.add(prometheusRegistry);
    REGISTRY.add(jmxMeterRegistry);

    new JvmMemoryMetrics().bindTo(REGISTRY);
    new JvmGcMetrics().bindTo(REGISTRY);
    new JvmThreadMetrics().bindTo(REGISTRY);
  }

  private static Timer createTimer(
      final String metricName, final List<Tag> tags, Duration maxExpectedDuration) {
    return Timer.builder(metricName)
        .publishPercentileHistogram()
        .maximumExpectedValue(maxExpectedDuration)
        .tags(tags)
        .register(REGISTRY);
  }

  public static void countAriCommand(final AriCommandType commandType) {
    Counter counter =
        switch (commandType) {
          case UNKNOWN -> COMMAND_TYPE_UNKOWN_INCOMING_MESSAGES_COUNTER;

          case BRIDGE_CREATION -> COMMAND_TYPE_BRIDGE_CREATION_INCOMING_MESSAGES_COUNTER;

          case BRIDGE -> COMMAND_TYPE_BRIDGE_INCOMING_MESSAGES_COUNTER;
          case CHANNEL_CREATION -> COMMAND_TYPE_CHANNEL_CREATION_INCOMING_MESSAGES_COUNTER;
          case CHANNEL -> COMMAND_TYPE_CHANNEL_INCOMING_MESSAGES_COUNTER;
          case PLAYBACK_CREATION -> COMMAND_TYPE_PLAYBACK_CREATION_INCOMING_MESSAGES_COUNTER;
          case PLAYBACK -> COMMAND_TYPE_PLAYBACK_INCOMING_MESSAGES_COUNTER;
          case RECORDING_CREATION -> COMMAND_TYPE_RECORDING_CREATION_INCOMING_MESSAGES_COUNTER;
          case RECORDING -> COMMAND_TYPE_RECORDING_INCOMING_MESSAGES_COUNTER;
          case SNOOPING_CREATION -> COMMAND_TYPE_SNOOPING_CREATION_INCOMING_MESSAGES_COUNTER;
          default -> {
            LOGGER.warn("Encountered unkown ARI Command Type: {}", commandType);
            yield COMMAND_TYPE_UNKOWN_INCOMING_MESSAGES_COUNTER;
          }
        };
    counter.increment();
  }
}
