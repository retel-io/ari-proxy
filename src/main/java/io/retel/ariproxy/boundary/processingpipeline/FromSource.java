package io.retel.ariproxy.boundary.processingpipeline;

import akka.NotUsed;
import akka.stream.javadsl.Source;

@FunctionalInterface
public interface FromSource<T> {
	ToSink from(Source<T, NotUsed> source);
}
