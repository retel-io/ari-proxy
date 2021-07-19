package io.retel.ariproxy.boundary.callcontext;

import static java.util.concurrent.CompletableFuture.completedFuture;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.api.*;
import io.retel.ariproxy.metrics.MetricsServiceMessage;
import io.retel.ariproxy.persistence.PersistenceStore;
import io.vavr.control.Try;
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

  public static Behavior<CallContextProviderMessage> create(
      final ActorRef<MetricsServiceMessage> metricsService) {

    final PerformanceMeteringKeyValueStore store =
        new PerformanceMeteringKeyValueStore(
            new CachedKeyValueStore(providePersistenceStore(), metricsService), metricsService);

    return create(store);
  }

  public static Behavior<CallContextProviderMessage> create(
      final KeyValueStore<String, String> store) {
    return Behaviors.setup(
        context ->
            Behaviors.receive(CallContextProviderMessage.class)
                .onMessage(RegisterCallContext.class, msg -> registerCallContextHandler(store, msg))
                .onMessage(ProvideCallContext.class, msg -> provideCallContextHandler(store, msg))
                .onMessage(ReportHealth.class, msg -> handleReportHealth(store, msg))
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
          final ProvideCallContextResponse response;
          if (error != null) {
            if (error instanceof CallContextLookupError) {
              response = (CallContextLookupError) error;
            } else {
              response =
                  new CallContextLookupError(
                      "Unable to lookup call context: " + error.getMessage());
            }
          } else {
            if (cContext.isPresent()) {
              response = new CallContextProvided(cContext.get());
            } else {
              response = new CallContextLookupError("Unable to lookup call context");
            }
          }

          msg.replyTo().tell(response);
        });

    return Behaviors.same();
  }

  private static CompletableFuture<Optional<String>> provideCallContextForLookupOnlyPolicy(
      final KeyValueStore<String, String> store, final ProvideCallContext msg) {
    return exceptionallyCompose(
            store.get(msg.resourceId()),
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
    if (msg.maybeCallContextFromChannelVars().isDefined()) {
      final String callContext =
          new CallContextProvided(msg.maybeCallContextFromChannelVars().get()).callContext();
      return store.put(msg.resourceId(), callContext).thenApply(done -> Optional.of(callContext));
    }

    return store
        .get(msg.resourceId())
        .thenCompose(
            maybeCallContextFromStore -> {
              if (maybeCallContextFromStore.isPresent()) {
                return completedFuture(maybeCallContextFromStore);
              }

              final String generatedCallContext = UUID.randomUUID().toString();
              return store
                  .put(msg.resourceId(), generatedCallContext)
                  .thenApply(done -> Optional.of(generatedCallContext));
            });
  }

  private static Behavior<CallContextProviderMessage> handleReportHealth(
      final KeyValueStore<String, String> store, final ReportHealth msg) {
    store.checkHealth().thenAccept(healthReport -> msg.replyTo().tell(healthReport));

    return Behaviors.same();
  }

  private static String withKeyPrefix(final String resourceId) {
    return KEY_PREFIX + ":" + resourceId;
  }

  private static PersistentKeyValueStore providePersistenceStore() {
    final Config serviceConfig = ConfigFactory.load().getConfig("service");

    final String persistenceStoreClassName =
        serviceConfig.hasPath("persistence-store")
            ? serviceConfig.getString("persistence-store")
            : "io.retel.ariproxy.persistence.plugin.RedisPersistenceStore";

    final PersistenceStore persistenceStore =
        Try.of(() -> Class.forName(persistenceStoreClassName))
            .flatMap(clazz -> Try.of(() -> clazz.getMethod("create")))
            .flatMap(method -> Try.of(() -> (PersistenceStore) method.invoke(null)))
            .getOrElseThrow(t -> new RuntimeException("Failed to load any PersistenceStore", t));

    return new PersistentKeyValueStore(persistenceStore);
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
