package io.retel.ariproxy.boundary.processingpipeline;

import io.retel.ariproxy.config.ServiceConfig;

@FunctionalInterface
public interface ProcessingPipeline<T, E> {
	OnSystem<T, E> withConfig(ServiceConfig config);
}
