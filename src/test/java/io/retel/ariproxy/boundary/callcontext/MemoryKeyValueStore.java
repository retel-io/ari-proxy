package io.retel.ariproxy.boundary.callcontext;

import static java.util.concurrent.CompletableFuture.completedFuture;

import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.persistence.KeyValueStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MemoryKeyValueStore implements KeyValueStore<String, String> {

  private final Map<String, String> store;

  public MemoryKeyValueStore() {
    this(new HashMap<>());
  }

  public MemoryKeyValueStore(final Map<String, String> store) {
    this.store = store;
  }

  @Override
  public CompletableFuture<Void> put(final String key, final String value) {
    store.put(key, value);
    return completedFuture(null);
  }

  @Override
  public CompletableFuture<Optional<String>> get(final String key) {
    return completedFuture(Optional.ofNullable(store.get(key)));
  }

  @Override
  public CompletableFuture<HealthReport> checkHealth() {
    return completedFuture(HealthReport.ok());
  }

  @Override
  public void close() {
    // intentionally left blank
  }
}
