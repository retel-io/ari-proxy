package io.retel.ariproxy.boundary.processingpipeline;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;

@FunctionalInterface
public interface WithCallContextProvider<T> {
  WithMetricsService<T> withCallContextProvider(
      ActorRef<CallContextProviderMessage> callContextProvider);
}
