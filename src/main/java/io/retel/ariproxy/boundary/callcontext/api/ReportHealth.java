package io.retel.ariproxy.boundary.callcontext.api;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.health.api.HealthReport;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public class ReportHealth implements CallContextProviderMessage {
  final ActorRef<HealthReport> replyTo;

  public ReportHealth(final ActorRef<HealthReport> replyTo) {
    this.replyTo = replyTo;
  }

  public ActorRef<HealthReport> replyTo() {
    return replyTo;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, SHORT_PREFIX_STYLE);
  }
}
