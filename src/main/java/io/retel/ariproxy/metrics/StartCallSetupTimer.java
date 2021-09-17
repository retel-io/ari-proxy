package io.retel.ariproxy.metrics;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import java.util.Optional;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class StartCallSetupTimer implements MetricsServiceMessage {
  private final String callContext;
  private final ActorRef<MetricRegistered> replyTo;

  public StartCallSetupTimer(String callContext) {
    this(callContext, null);
  }

  public StartCallSetupTimer(final String callContext, final ActorRef<MetricRegistered> replyTo) {
    this.callContext = callContext;
    this.replyTo = replyTo;
  }

  public String getCallContext() {
    return callContext;
  }

  public Optional<ActorRef<MetricRegistered>> getReplyTo() {
    return Optional.ofNullable(replyTo);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
