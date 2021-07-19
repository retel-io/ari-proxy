package io.retel.ariproxy.persistence;

import io.retel.ariproxy.health.api.HealthReport;
import io.vavr.Value;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;

public class PersistentKeyValueStore implements KeyValueStore<String, String> {

  private final PersistenceStore persistenceStore;

  public PersistentKeyValueStore(final PersistenceStore persistenceStore) {
    this.persistenceStore = persistenceStore;
  }

  @Override
  public CompletableFuture<Void> put(final String key, final String value) {
    return persistenceStore.set(key, value).<Void>map(s -> null).toCompletableFuture();
  }

  @Override
  public CompletableFuture<Optional<String>> get(final String key) {
    return persistenceStore.get(key).map(Value::toJavaOptional).toCompletableFuture();
  }

  @Override
  public CompletableFuture<HealthReport> checkHealth() {
    final String key = "HEALTHCHECK_" + UUID.randomUUID();
    final String value = StringUtils.reverse(key);

    return persistenceStore
        .set(key, value)
        .toCompletableFuture()
        .thenCompose(unusedValue -> persistenceStore.get(key).toCompletableFuture())
        .handle(
            (result, error) -> {
              if (error != null) {
                return HealthReport.error(
                    "RedisCheck: unable to set & get value: " + error.getMessage());
              }

              if (result.isEmpty()) {
                return HealthReport.error("RedisCheck: empty result on get()");
              }

              if (!result.get().equals(value)) {
                return HealthReport.error(
                    String.format(
                        "RedisCheck: %s does not match expected %s", result.get(), value));
              }

              return HealthReport.ok();
            });
  }

  @Override
  public void close() {
    persistenceStore.shutdown();
  }
}
