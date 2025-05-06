package io.retel.ariproxy.persistence.plugin;

import static io.vavr.API.None;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.vavr.control.Option;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisPersistenceStoreTest {

  private static final String theValue = "value";
  private static final String theKey = "key";

  @Test
  void getShouldBePassedToUnderlyingRedisClient() {
    RedisClient redisClient = mock(RedisClient.class);
    StatefulRedisConnection connection = mock(StatefulRedisConnection.class);
    RedisCommands commands = mock(RedisCommands.class);
    when(redisClient.connect()).thenReturn(connection);
    when(connection.sync()).thenReturn(commands);
    when(commands.get(anyString())).thenReturn(theValue);

    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient, Option.none());

    assertThat(store.get(theKey).await().get().get(), is(theValue));
  }

  @Test
  void getShouldBePassedToUnderlyingRedisClientAndReturnNone() {
    RedisClient redisClient = mock(RedisClient.class);
    StatefulRedisConnection connection = mock(StatefulRedisConnection.class);
    RedisCommands commands = mock(RedisCommands.class);
    when(redisClient.connect()).thenReturn(connection);
    when(connection.sync()).thenReturn(commands);
    when(commands.get(anyString())).thenReturn(null);

    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient, Option.none());

    assertThat(store.get(theKey).await().get(), is(None()));
  }

  @Test
  void setShouldBePassedToUnderlyingRedisClient() {
    RedisClient redisClient = mock(RedisClient.class);
    StatefulRedisConnection connection = mock(StatefulRedisConnection.class);
    RedisCommands commands = mock(RedisCommands.class);
    when(redisClient.connect()).thenReturn(connection);
    when(connection.sync()).thenReturn(commands);
    when(commands.set(anyString(), anyString(), any(SetArgs.class))).thenReturn(theValue);
    when(commands.expire(anyString(), any(Duration.class))).thenReturn(true);

    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient, Option.none());

    assertThat(store.set(theKey, theValue).await().get(), is(theValue));
    verify(commands, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  void setAndExpireShouldBePassedToUnderlyingRedisClient() {
    RedisClient redisClient = mock(RedisClient.class);
    StatefulRedisConnection connection = mock(StatefulRedisConnection.class);
    RedisCommands commands = mock(RedisCommands.class);
    when(redisClient.connect()).thenReturn(connection);
    when(connection.sync()).thenReturn(commands);
    when(commands.set(anyString(), anyString(), any(SetArgs.class))).thenReturn(theValue);
    when(commands.expire(anyString(), any(Duration.class))).thenReturn(true);

    RedisPersistenceStore store =
        RedisPersistenceStore.create(redisClient, Option.some(Duration.ofSeconds(1)));

    assertThat(store.set(theKey, theValue).await().get(), is(theValue));

    verify(commands).expire(anyString(), eq(Duration.ofSeconds(1)));
  }

  @Test
  void shutdown() {
    RedisClient redisClient = mock(RedisClient.class);
    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient, Option.none());

    store.shutdown();

    verify(redisClient, times(1)).shutdown();
  }
}
