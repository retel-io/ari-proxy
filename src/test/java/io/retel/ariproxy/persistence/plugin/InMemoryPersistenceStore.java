package io.retel.ariproxy.persistence.plugin;

import io.retel.ariproxy.persistence.PersistenceStore;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;

public class InMemoryPersistenceStore implements PersistenceStore {

  private Map<String, String> store = HashMap.empty();

  @Override
  public Future<String> set(String key, String value) {
    if (key.contains("failure")) {
      return Future.failed(new Exception("Failed to set value for key"));
    }
    store = store.put(key, value);
    return Future.successful(value);
  }

  @Override
  public Future<Option<String>> get(String key) {
    return Future.successful(store.get(key));
  }

  @Override
  public void shutdown() {}

  public static InMemoryPersistenceStore create() {
    return new InMemoryPersistenceStore();
  }
}
