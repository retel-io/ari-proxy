package io.retel.ariproxy.boundary.processingpipeline;

import akka.actor.ActorSystem;

@FunctionalInterface
public interface ProcessingPipeline<T, E> {
	WithHandler<T, E> on(ActorSystem system);
}
