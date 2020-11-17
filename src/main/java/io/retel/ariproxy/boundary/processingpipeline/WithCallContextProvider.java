package io.retel.ariproxy.boundary.processingpipeline;

import akka.actor.ActorRef;

@FunctionalInterface
public interface WithCallContextProvider<T> {
	WithMetricsService<T> withCallContextProvider(ActorRef callContextProvider);
}
