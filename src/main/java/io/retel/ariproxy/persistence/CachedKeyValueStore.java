package io.retel.ariproxy.persistence;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.metrics.Metrics;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedKeyValueStore implements KeyValueStore<String, String> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CachedKeyValueStore.class);

  private final KeyValueStore<String, String> store;
  private final LoadingCache<String, Optional<String>> cache;

  public CachedKeyValueStore(final KeyValueStore<String, String> store) {
    this.store = store;

    cache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(6, TimeUnit.HOURS)
            .build(
                CacheLoader.from(
                    (String key) -> {
                      try {
                        final Optional<String> result = store.get(key).get();
                        Metrics.countCacheReadMiss();

                        return result;
                      } catch (InterruptedException | ExecutionException e) {
                        LOGGER.warn("Unable to retrieve value for key {} from store", key, e);
                        Metrics.countPersistenceReadError();

                        return Optional.empty();
                      }
                    }));
  }

  @Override
  public CompletableFuture<Void> put(final String key, final String value) {
    cache.put(key, Optional.of(value));
    return store.put(key, value);
  }

  @Override
  public CompletableFuture<Optional<String>> get(final String key) {
    try {
      Metrics.countCacheReadAttempt();
      return CompletableFuture.completedFuture(cache.get(key));
    } catch (ExecutionException e) {
      LOGGER.error("Unable to get value for key {} from cache", key, e);
      Metrics.countCacheReadError();
      return CompletableFuture.completedFuture(Optional.empty());
    }
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
