package io.retel.ariproxy.persistence;

import akka.actor.typed.ActorRef;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.metrics.MetricsServiceMessage;
import io.retel.ariproxy.metrics.RedisUpdateTimerStart;
import io.retel.ariproxy.metrics.RedisUpdateTimerStop;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PerformanceMeteringKeyValueStore implements KeyValueStore<String, String> {

  private final KeyValueStore<String, String> store;
  private final ActorRef<MetricsServiceMessage> metricsService;

  public PerformanceMeteringKeyValueStore(
      final KeyValueStore<String, String> store,
      final ActorRef<MetricsServiceMessage> metricsService) {
    this.store = store;
    this.metricsService = metricsService;
  }

  @Override
  public CompletableFuture<Void> put(final String key, final String value) {
    final String metricsContext = UUID.randomUUID().toString();
    metricsService.tell(new RedisUpdateTimerStart(metricsContext));

    return store
        .put(key, value)
        .thenRun(
            () -> {
              metricsService.tell(new RedisUpdateTimerStop(metricsContext));
            });
  }

  @Override
  public CompletableFuture<Optional<String>> get(final String key) {
    return store.get(key);
  }

  @Override
  public CompletableFuture<HealthReport> checkHealth() {
    return store.checkHealth();
  }
}
