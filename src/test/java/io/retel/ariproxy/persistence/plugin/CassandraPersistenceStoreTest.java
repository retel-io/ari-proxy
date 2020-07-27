package io.retel.ariproxy.persistence.plugin;

import static io.vavr.API.None;
import static io.vavr.API.Some;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;

import com.datastax.oss.driver.api.core.CqlSession;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vavr.concurrent.Future;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class CassandraPersistenceStoreTest {

  private final static String THE_KEY = "key";
  private final static String THE_VALUE = "value";

  @Test
  @Disabled("needs configured backend")
  public void testReadWriteReal() {
    final CqlSession session = createCqlSession();
    final CassandraPersistenceStore store = new CassandraPersistenceStore(session);

    assertThat(store.get(THE_KEY).get(), is(None()));

    assertThat(store.set(THE_KEY, THE_VALUE).await().isSuccess(), is(true));

    assertThat(store.get(THE_KEY).get(), is(Some(THE_VALUE)));
  }

  private CqlSession createCqlSession() {
    final Config config = ConfigFactory.load();
    final String authProvider = config
        .getString("datastax-java-driver.advanced.auth-provider.class");
    assertThat("datastax-java-driver config missing ", authProvider, is(not(emptyString())));

    final CqlSession session = CqlSession.builder().build();
    session.execute("TRUNCATE TABLE retel");

    return session;
  }

}