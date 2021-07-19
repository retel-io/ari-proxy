package io.retel.ariproxy.metrics;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import java.util.Optional;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class IncreaseCounter implements MetricsServiceMessage {

  private final String name;
  private final ActorRef<MetricRegistered> replyTo;

  public IncreaseCounter(final String name) {
    this(name, null);
  }

  public IncreaseCounter(final String name, final ActorRef<MetricRegistered> replyTo) {
    this.name = name;
    this.replyTo = replyTo;
  }

  public String getName() {
    return name;
  }

  public Optional<ActorRef<MetricRegistered>> getReplyTo() {
    return Optional.ofNullable(replyTo);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
