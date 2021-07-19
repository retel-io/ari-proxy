package io.retel.ariproxy.persistence;

import akka.actor.typed.ActorRef;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.metrics.MetricsServiceMessage;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import io.vavr.control.Try;

public interface PersistenceStore {

	Future<String> set(String key, String value);

	Future<Option<String>> get(String key);

	void shutdown();
}
