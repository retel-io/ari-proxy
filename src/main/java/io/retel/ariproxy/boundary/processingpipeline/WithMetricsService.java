package io.retel.ariproxy.boundary.processingpipeline;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.metrics.MetricsServiceMessage;

@FunctionalInterface
public interface WithMetricsService<T> {
  FromSource<T> withMetricsService(ActorRef<MetricsServiceMessage> metricsService);
}
