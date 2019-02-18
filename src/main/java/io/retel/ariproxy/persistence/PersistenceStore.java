package io.retel.ariproxy.persistence;

import io.vavr.concurrent.Future;
import io.vavr.control.Option;

public interface PersistenceStore {

	Future<String> set(String key, String value);

	Future<Option<String>> get(String key);

	void shutdown();
}
