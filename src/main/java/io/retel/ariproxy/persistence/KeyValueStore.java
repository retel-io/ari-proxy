package io.retel.ariproxy.persistence;

import akka.actor.typed.ActorRef;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.metrics.MetricsServiceMessage;
import io.vavr.control.Try;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface KeyValueStore<K, V> extends AutoCloseable {
  CompletableFuture<Void> put(K key, V value);

  CompletableFuture<Optional<V>> get(K key);

  CompletableFuture<HealthReport> checkHealth();

  static KeyValueStore<String, String> createDefaultStore(
      final ActorRef<MetricsServiceMessage> metricsService) {

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

    return new PerformanceMeteringKeyValueStore(
        new CachedKeyValueStore(new PersistentKeyValueStore(persistenceStore), metricsService),
        metricsService);
  }
}
