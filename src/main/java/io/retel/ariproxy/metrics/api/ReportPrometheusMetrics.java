package io.retel.ariproxy.metrics.api;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.metrics.MetricsServiceMessage;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public class ReportPrometheusMetrics implements MetricsServiceMessage {
  final ActorRef<PrometheusMetricsReport> replyTo;

  public ReportPrometheusMetrics(final ActorRef<PrometheusMetricsReport> replyTo) {
    this.replyTo = replyTo;
  }

  public ActorRef<PrometheusMetricsReport> replyTo() {
    return replyTo;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, SHORT_PREFIX_STYLE);
  }
}
