package io.retel.ariproxy.boundary.callcontext;

import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import scala.concurrent.ExecutionContext;

interface PersistenceStore {

	Future<String> set(String key, String value);

	Future<Option<String>> get(String key);

	void shutdown();
}
