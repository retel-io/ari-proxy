package io.retel.ariproxy.persistence.plugin;

import static io.vavr.API.None;
import static io.vavr.API.Some;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import java.io.IOException;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CassandraPersistenceStoreTest {

  private final static String THE_KEY = "key";
  private final static String THE_VALUE = "value";
  private CqlSession cqlSession;


  @BeforeEach
  public void setup() throws IOException, InterruptedException {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra();
    cqlSession = EmbeddedCassandraServerHelper.getSession();
    new CQLDataLoader(cqlSession).load(new ClassPathCQLDataSet("persistence/cassandra.cql"));
  }

  @AfterEach
  public void teardown() {
    cqlSession.close();
  }

  @Test
  public void testReadWriteReal() {
    final CassandraPersistenceStore store = new CassandraPersistenceStore(cqlSession);

    assertThat(store.get(THE_KEY).get(), is(None()));

    assertThat(store.set(THE_KEY, THE_VALUE).await().isSuccess(), is(true));

    assertThat(store.get(THE_KEY).get(), is(Some(THE_VALUE)));
  }

}
