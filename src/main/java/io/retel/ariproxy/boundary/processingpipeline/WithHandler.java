package io.retel.ariproxy.boundary.processingpipeline;

@FunctionalInterface
public interface WithHandler<T, E> {
	WithCallContextProvider<T> withHandler(E handler);
}
