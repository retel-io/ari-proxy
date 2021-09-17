package io.retel.ariproxy.metrics;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import java.util.Optional;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class StopCallSetupTimer implements MetricsServiceMessage {

  private final String callcontext;
  private final String application;
  private final ActorRef<MetricRegistered> replyTo;

  public StopCallSetupTimer(String callcontext, String application) {
    this(callcontext, application, null);
  }

  public StopCallSetupTimer(
      final String callcontext,
      final String application,
      final ActorRef<MetricRegistered> replyTo) {
    this.callcontext = callcontext;
    this.application = application;
    this.replyTo = replyTo;
  }

  public String getCallcontext() {
    return callcontext;
  }

  public String getApplication() {
    return application;
  }

  public Optional<ActorRef<MetricRegistered>> getReplyTo() {
    return Optional.ofNullable(replyTo);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
