package io.retel.ariproxy.persistence.plugin;

import static io.vavr.API.None;
import static io.vavr.API.Some;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SQLitePersistenceStoreTest {

  private SQLitePersistenceStore store;

  @BeforeEach
  void setup() {
    store = SQLitePersistenceStore.create();
  }

  @AfterEach
  void teardown() {
    store.shutdown();
  }

  @Test
  void testReadWriteReal() {
    assertThat(store.get("key").get(), is(None()));

    assertThat(store.set("key", "value").await().isSuccess(), is(true));

    assertThat(store.get("key").get(), is(Some("value")));
  }

  @Test
  void shouldCleanupOldEntries() {
    assertThat(store.get("cleanupKey").get(), is(None()));
    assertThat(store.set("cleanupKey", "value").await().isSuccess(), is(true));
    assertThat(store.get("cleanupKey").get(), is(Some("value")));

    await()
        .atMost(Duration.ofSeconds(4))
        .until(
            () -> {
              store.set(UUID.randomUUID().toString(), "value");
              return store.get("cleanupKey").get().isEmpty();
            });
  }
}
