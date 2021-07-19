package io.retel.ariproxy.boundary.callcontext;

import io.retel.ariproxy.health.api.HealthReport;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface KeyValueStore<K, V> {
  CompletableFuture<Void> put(K key, V value);

  CompletableFuture<Optional<V>> get(K key);

  CompletableFuture<HealthReport> checkHealth();
}
