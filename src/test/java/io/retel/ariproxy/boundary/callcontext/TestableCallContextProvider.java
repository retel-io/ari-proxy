package io.retel.ariproxy.boundary.callcontext;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Signal;
import akka.actor.typed.javadsl.Behaviors;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.persistence.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestableCallContextProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestableCallContextProvider.class);

  private final TestProbe<CallContextProviderMessage> probe;
  private final ActorRef<CallContextProviderMessage> ref;

  public TestableCallContextProvider(final ActorTestKit testKit) {
    this(testKit, new MemoryKeyValueStore());
  }

  public TestableCallContextProvider(
      final ActorTestKit testKit, final KeyValueStore<String, String> store) {
    final ActorRef<CallContextProviderMessage> actualCallContextProvider =
        testKit.spawn(CallContextProvider.create(store));
    probe = testKit.createTestProbe(CallContextProviderMessage.class);

    ref =
        testKit.spawn(
            Behaviors.receive(CallContextProviderMessage.class)
                .onMessage(
                    CallContextProviderMessage.class,
                    msg -> {
                      LOGGER.debug("Received message: {}", msg);
                      probe.ref().tell(msg);
                      actualCallContextProvider.tell(msg);

                      return Behaviors.same();
                    })
                .onSignal(
                    Signal.class,
                    signal -> {
                      LOGGER.debug("Received signal: {}", signal);
                      return Behaviors.same();
                    })
                .build());
  }

  public TestProbe<CallContextProviderMessage> probe() {
    return probe;
  }

  public ActorRef<CallContextProviderMessage> ref() {
    return ref;
  }
}
