package io.retel.ariproxy.boundary.callcontext;

import static java.util.concurrent.CompletableFuture.completedFuture;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.PreRestart;
import akka.actor.typed.javadsl.Behaviors;
import akka.pattern.StatusReply;
import io.retel.ariproxy.boundary.callcontext.api.*;
import io.retel.ariproxy.persistence.KeyValueStore;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallContextProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(CallContextProvider.class);

  private static final String KEY_PREFIX = "ari-proxy:call-context-provider";

  private CallContextProvider() {
    throw new IllegalStateException("Utility class");
  }

  public static Behavior<CallContextProviderMessage> create() {
    return create(KeyValueStore.createDefaultStore());
  }

  public static Behavior<CallContextProviderMessage> create(
      final KeyValueStore<String, String> store) {
    return Behaviors.setup(
        context ->
            Behaviors.receive(CallContextProviderMessage.class)
                .onMessage(RegisterCallContext.class, msg -> registerCallContextHandler(store, msg))
                .onMessage(ProvideCallContext.class, msg -> provideCallContextHandler(store, msg))
                .onMessage(ReportHealth.class, msg -> handleReportHealth(store, msg))
                .onSignal(PostStop.class, signal -> cleanup(store))
                .onSignal(PreRestart.class, signal -> cleanup(store))
                .build());
  }

  private static Behavior<CallContextProviderMessage> registerCallContextHandler(
      final KeyValueStore<String, String> store, final RegisterCallContext msg) {
    final String resourceId = msg.resourceId();
    final String callContext = msg.callContext();
    LOGGER.debug("Registering resourceId '{}' => callContext '{}'…", resourceId, callContext);

    store
        .put(withKeyPrefix(resourceId), callContext)
        .thenRunAsync(
            () -> {
              LOGGER.debug(
                  "Successfully registered resourceId '{}' => callContext '{}'",
                  resourceId,
                  callContext);
              msg.replyTo().tell(new CallContextRegistered(resourceId, callContext));
            });

    return Behaviors.same();
  }

  private static Behavior<CallContextProviderMessage> provideCallContextHandler(
      final KeyValueStore<String, String> store, final ProvideCallContext msg) {
    LOGGER.debug("Looking up callContext for resourceId '{}'…", msg.resourceId());

    final CompletableFuture<Optional<String>> callContext =
        ProviderPolicy.CREATE_IF_MISSING.equals(msg.policy())
            ? provideCallContextForCreateIfMissingPolicy(store, msg)
            : provideCallContextForLookupOnlyPolicy(store, msg);

    callContext.whenComplete(
        (cContext, error) -> {
          final StatusReply<CallContextProvided> response;
          if (error != null) {
            if (error instanceof CallContextLookupError) {
              response = StatusReply.error(error);
            } else {
              response =
                  StatusReply.error(
                      String.format(
                          "Unable to lookup call context for resource %s: %s",
                          msg.resourceId(), error.getMessage()));
            }
          } else {
            if (cContext.isPresent()) {
              response = StatusReply.success(new CallContextProvided(cContext.get()));
            } else {
              response =
                  StatusReply.error(
                      String.format(
                          "Unable to lookup call context for resource %s", msg.resourceId()));
            }
          }

          msg.replyTo().tell(response);
        });

    return Behaviors.same();
  }

  private static CompletableFuture<Optional<String>> provideCallContextForLookupOnlyPolicy(
      final KeyValueStore<String, String> store, final ProvideCallContext msg) {
    final String prefixedResourceId = withKeyPrefix(msg.resourceId());
    return exceptionallyCompose(
            store.get(prefixedResourceId),
            error ->
                failedFuture(
                    new CallContextLookupError(
                        String.format(
                            "Failed to lookup call context for resource id %s...",
                            msg.resourceId()))))
        .toCompletableFuture();
  }

  private static CompletableFuture<Optional<String>> provideCallContextForCreateIfMissingPolicy(
      final KeyValueStore<String, String> store, final ProvideCallContext msg) {
    final String prefixedResourceId = withKeyPrefix(msg.resourceId());

    if (msg.maybeCallContextFromChannelVars().isDefined()) {
      final String callContext =
          new CallContextProvided(msg.maybeCallContextFromChannelVars().get()).callContext();
      return store.put(prefixedResourceId, callContext).thenApply(done -> Optional.of(callContext));
    }

    return store
        .get(prefixedResourceId)
        .thenCompose(
            maybeCallContextFromStore -> {
              if (maybeCallContextFromStore.isPresent()) {
                return completedFuture(maybeCallContextFromStore);
              }

              final String generatedCallContext = UUID.randomUUID().toString();
              return store
                  .put(prefixedResourceId, generatedCallContext)
                  .thenApply(done -> Optional.of(generatedCallContext));
            });
  }

  private static Behavior<CallContextProviderMessage> handleReportHealth(
      final KeyValueStore<String, String> store, final ReportHealth msg) {
    store.checkHealth().thenAccept(healthReport -> msg.replyTo().tell(healthReport));

    return Behaviors.same();
  }

  private static Behavior<CallContextProviderMessage> cleanup(
      final KeyValueStore<String, String> store) {
    try {
      store.close();
    } catch (Exception e) {
      LOGGER.warn("Unable to close store", e);
    }

    return Behaviors.same();
  }

  private static String withKeyPrefix(final String resourceId) {
    return KEY_PREFIX + ":" + resourceId;
  }

  private static <T> CompletableFuture<T> failedFuture(final Throwable error) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(error);
    return future;
  }

  private static <T> CompletionStage<T> exceptionallyCompose(
      final CompletionStage<T> stage, final Function<Throwable, ? extends CompletionStage<T>> fn) {
    return stage
        .<CompletionStage<T>>thenApply(CompletableFuture::completedFuture)
        .exceptionally(fn)
        .thenCompose(Function.identity());
  }
}
