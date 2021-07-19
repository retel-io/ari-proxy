package io.retel.ariproxy.metrics;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class StartCallSetupTimer implements MetricsServiceMessage {
  private final String callContext;
  private final ActorRef<MetricRegistered> replyTo;

  @Deprecated
  public StartCallSetupTimer(String callContext) {
    this(callContext, null); // TODO
  }

  public StartCallSetupTimer(final String callContext, final ActorRef<MetricRegistered> replyTo) {
    this.callContext = callContext;
    this.replyTo = replyTo;
  }

  public String getCallContext() {
    return callContext;
  }

  public ActorRef<MetricRegistered> getReplyTo() {
    return replyTo;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
