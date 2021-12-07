package io.retel.ariproxy.persistence;

import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.metrics.Metrics;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PerformanceMeteringKeyValueStore implements KeyValueStore<String, String> {

  private final KeyValueStore<String, String> store;

  public PerformanceMeteringKeyValueStore(final KeyValueStore<String, String> store) {
    this.store = store;
  }

  @Override
  public CompletableFuture<Void> put(final String key, final String value) {
    final Instant start = Instant.now();

    return store
        .put(key, value)
        .thenRun(
            () -> Metrics.timePersistenceWriteDuration(Duration.between(start, Instant.now())));
  }

  @Override
  public CompletableFuture<Optional<String>> get(final String key) {
    return store.get(key);
  }

  @Override
  public CompletableFuture<HealthReport> checkHealth() {
    return store.checkHealth();
  }

  @Override
  public void close() throws Exception {
    store.close();
  }
}
