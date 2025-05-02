package io.retel.ariproxy.persistence.plugin;

import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.persistence.PersistenceStore;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLitePersistenceStore implements PersistenceStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(SQLitePersistenceStore.class);

  private static Duration entryTtl;

  private final Connection connection;

  SQLitePersistenceStore(final Connection connection) {
    this.connection = connection;
  }

  public static SQLitePersistenceStore create() {
    final var config = ConfigFactory.load().getConfig("service").getConfig("sqlite");
    entryTtl = config.getDuration("ttl");

    try {
      final var connection = DriverManager.getConnection(config.getString("url"));

      try (var statement = connection.createStatement()) {
        statement.execute("PRAGMA journal_mode=WAL;");
        statement.execute(
            """
                    create table if not exists ari_proxy
                    (
                        key   TEXT not null
                            primary key,
                        value TEXT not null,
                        created_at integer not null
                    );
                    """);
        statement.execute(
            """
create index if not exists ari_proxy_created_at_index on ari_proxy (created_at desc);
""");
      }

      return new SQLitePersistenceStore(connection);
    } catch (SQLException e) {
      LOGGER.error("Could not initialize SQLite database", e);
      throw new RuntimeException(e);
    }
  }

  public static String getName() {
    return "SQLite";
  }

  @Override
  public Future<Option<String>> get(final String key) {
    return Future.of(
        () -> {
          try (final var statement =
              connection.prepareStatement("select value from ari_proxy where key = ?")) {

            statement.setString(1, key);

            return Option.of(statement.executeQuery().getString("value"));

          } catch (SQLException e) {
            LOGGER.error("Could not get value for key '{}'", key, e);
          }
          return Option.none();
        });
  }

  @Override
  public Future<String> set(final String key, final String value) {
    return Future.of(
        () -> {
          try (final var statement =
              connection.prepareStatement("insert into ari_proxy values(?,?,?)")) {

            statement.setString(1, key);
            statement.setString(2, value);
            statement.setLong(3, Instant.now().getEpochSecond());

            statement.execute();

            cleanupOldEntries();
          } catch (SQLException e) {
            LOGGER.error("Could not set value '{}' for key '{}'", value, key, e);
          }
          return key;
        });
  }

  private void cleanupOldEntries() {
    try (final var statement =
        connection.prepareStatement(
            "delete from ari_proxy where ari_proxy.created_at < unixepoch('now') - ?")) {
      statement.setLong(1, entryTtl.toSeconds());
      statement.execute();
    } catch (SQLException e) {
      LOGGER.error("Could not cleanup old entries", e);
    }
  }

  @Override
  public void shutdown() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
