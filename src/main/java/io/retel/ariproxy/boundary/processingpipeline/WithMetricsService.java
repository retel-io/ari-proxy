package io.retel.ariproxy.boundary.processingpipeline;

import akka.actor.ActorRef;

@FunctionalInterface
public interface WithMetricsService<T> {
	FromSource<T> withMetricsService(ActorRef metricsService);
}
