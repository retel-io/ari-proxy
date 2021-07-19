package io.retel.ariproxy.metrics;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class RedisUpdateTimerStart implements MetricsServiceMessage {
  private final String context;
  private final ActorRef<MetricRegistered> replyTo;

  public RedisUpdateTimerStart(String context, final ActorRef<MetricRegistered> replyTo) {
    this.context = context;
    this.replyTo = replyTo;
  }

  public String getContext() {
    return context;
  }

  public ActorRef<MetricRegistered> getReplyTo() {
    return replyTo;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
