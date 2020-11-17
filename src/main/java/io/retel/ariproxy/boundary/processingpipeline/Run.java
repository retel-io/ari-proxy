package io.retel.ariproxy.boundary.processingpipeline;

import akka.stream.ActorMaterializer;

@FunctionalInterface
public interface Run {
	ActorMaterializer run();
}
