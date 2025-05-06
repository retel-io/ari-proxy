package io.retel.ariproxy.persistence.plugin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs.Builder;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.retel.ariproxy.persistence.PersistenceStore;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

public class RedisPersistenceStore implements PersistenceStore {

  private static final String SERVICE = "service";
  private static final String REDIS = "redis";
  private static final String HOST = "host";
  private static final String PORT = "port";
  private static final String DB = "db";
  private final Option<Duration> entryTtl;

  private final RedisClient redisClient;

  public RedisPersistenceStore(RedisClient redisClient, final Option<Duration> entryTtl) {
    Objects.requireNonNull(redisClient, "No RedisClient provided");
    this.redisClient = redisClient;
    this.entryTtl = entryTtl;
  }

  public static RedisPersistenceStore create() {

    final Config cfg = ConfigFactory.load().getConfig(SERVICE).getConfig(REDIS);
    final String host = cfg.getString(HOST);
    final int port = cfg.getInt(PORT);
    final int db = cfg.getInt(DB);

    final Option<Duration> entryTtl = Try.of(() -> cfg.getDuration("ttl")).toOption();

    return create(
        RedisClient.create(
            RedisURI.Builder.redis(host).withPort(port).withSsl(false).withDatabase(db).build()),
        entryTtl);
  }

  public static RedisPersistenceStore create(
      RedisClient redisClient, final Option<Duration> entryTtl) {
    return new RedisPersistenceStore(redisClient, entryTtl);
  }

  public static String getName() {
    return "Redis";
  }

  @Override
  public Future<String> set(String key, String value) {
    return executeRedisCommand(
        commands -> {
          final var setKey = commands.set(key, value, Builder.ex(21600));
          entryTtl.forEach(ttl -> commands.expire(key, ttl));
          return setKey;
        });
  }

  @Override
  public Future<Option<String>> get(String key) {
    return executeRedisCommand(commands -> Option.of(commands.get(key)));
  }

  private <T> Future<T> executeRedisCommand(Function<RedisCommands<String, String>, T> f) {
    return Future.of(
        () -> {
          try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            return f.apply(connection.sync());
          }
        });
  }

  @Override
  public void shutdown() {
    this.redisClient.shutdown();
  }
}
