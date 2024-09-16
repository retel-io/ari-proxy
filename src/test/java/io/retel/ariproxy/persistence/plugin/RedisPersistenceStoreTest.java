package io.retel.ariproxy.persistence.plugin;

import static io.vavr.API.None;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
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

    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient);

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

    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient);

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

    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient);

    assertThat(store.set(theKey, theValue).await().get(), is(theValue));
  }

  @Test
  void shutdown() {
    RedisClient redisClient = mock(RedisClient.class);
    RedisPersistenceStore store = RedisPersistenceStore.create(redisClient);

    store.shutdown();

    verify(redisClient, times(1)).shutdown();
  }
}
