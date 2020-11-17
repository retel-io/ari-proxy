package io.retel.ariproxy.boundary.processingpipeline;

import akka.NotUsed;
import akka.stream.javadsl.Sink;
import org.apache.kafka.clients.producer.ProducerRecord;

@FunctionalInterface
public interface ToSink {
	Run to(Sink<ProducerRecord<String, String>, NotUsed> sink);
}
