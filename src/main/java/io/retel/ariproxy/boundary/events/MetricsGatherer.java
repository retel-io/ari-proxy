package io.retel.ariproxy.boundary.events;

import io.retel.ariproxy.metrics.MetricsServiceMessage;
import java.util.function.Supplier;

@FunctionalInterface
interface MetricsGatherer {
  MetricsServiceMessage withCallContextSupplier(Supplier<String> callContextSupplier);
}
