package io.retel.ariproxy.persistence;

import static io.vavr.API.Some;

import akka.actor.AbstractLoggingActor;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.typesafe.config.ConfigFactory;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class PersistentCache extends AbstractLoggingActor {

	private PersistenceStore persistenceStore = null;

	private LoadingCache<String, Future<Option<String>>> cache = CacheBuilder.newBuilder()
			.expireAfterWrite(6, TimeUnit.HOURS)
			.build(CacheLoader.from(key -> persistenceStore.get(key)));

	abstract protected String keyPrefix();

	protected Future<SetDone> update(String key, String value) {
		String prefixedKey = keyPrefix() + ":" + key;
		cache.put(prefixedKey, Future.successful(Some(value)));
		return persistenceStore.set(prefixedKey, value).map(v -> new SetDone(prefixedKey, value));
	}

	protected Future<Option<String>> query(String key) throws ExecutionException {
		String prefixedKey = keyPrefix() + ":" + key;
		return cache.get(prefixedKey);
	}

	@Override
	public void preStart() throws Exception {
		super.preStart();
		persistenceStore = providePersistenceStore();
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();
		persistenceStore.shutdown();
	}

	private PersistenceStore providePersistenceStore() {

		final String persistenceStoreClassName = ConfigFactory.load().getConfig("service").getString("persistence-store");

		return Try.of(() -> Class.forName(persistenceStoreClassName))
				.flatMap(clazz -> Try.of(() -> clazz.getMethod("create")))
				.flatMap(method -> Try.of(() -> (PersistenceStore)method.invoke(null)))
				.getOrElseThrow(t -> new RuntimeException("Failed to load any PersistenceStore", t));
	}
}
