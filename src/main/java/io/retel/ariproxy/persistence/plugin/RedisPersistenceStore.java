package io.retel.ariproxy.persistence.plugin;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs.Builder;
import io.lettuce.core.api.StatefulRedisConnection;
import io.retel.ariproxy.config.ConfigLoader;
import io.retel.ariproxy.config.ServiceConfig;
import io.retel.ariproxy.persistence.PersistenceStore;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;

public class RedisPersistenceStore implements PersistenceStore {

	private final RedisClient redisClient;

	public RedisPersistenceStore(RedisClient redisClient) {
		this.redisClient = redisClient;
	}

	public static RedisPersistenceStore create() {

		final ServiceConfig config = ConfigLoader.load();

		return create(RedisClient.create(RedisURI.Builder
				.redis(config.getRedisHost(), config.getHttpPort())
				.withSsl(false)
				.withDatabase(config.getRedisDb())
				.build()));
	}

	public static RedisPersistenceStore create(RedisClient redisClient) {
		return new RedisPersistenceStore(redisClient);
	}

	@Override
	public Future<String> set(String key, String value) {
		return Future.of(() -> {
			try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
				return connection.sync().set(key, value, Builder.ex(21600));
			}
		});
	}

	@Override
	public Future<Option<String>> get(String key) {
		return Future.of(() -> {
			try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
				return Option.of(connection.sync().get(key));
			}
		});
	}

	@Override
	public void shutdown() {
		this.redisClient.shutdown();
	}
}
