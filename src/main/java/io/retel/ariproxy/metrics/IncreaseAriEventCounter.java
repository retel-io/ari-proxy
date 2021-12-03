package io.retel.ariproxy.metrics;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Optional;

public class IncreaseAriEventCounter implements MetricsServiceMessage {

  private final AriMessageType eventType;
  private final ActorRef<MetricRegistered> replyTo;

  public IncreaseAriEventCounter(final AriMessageType eventType) {
    this(eventType, null);
  }

  public IncreaseAriEventCounter(final AriMessageType eventType, final ActorRef<MetricRegistered> replyTo) {
    this.eventType = eventType;
    this.replyTo = replyTo;
  }

  public AriMessageType getEventType() {
    return eventType;
  }

  public Optional<ActorRef<MetricRegistered>> getReplyTo() {
    return Optional.ofNullable(replyTo);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
