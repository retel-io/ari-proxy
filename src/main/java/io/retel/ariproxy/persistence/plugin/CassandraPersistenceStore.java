package io.retel.ariproxy.persistence.plugin;

import static java.lang.String.format;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import io.retel.ariproxy.persistence.PersistenceStore;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;

public class CassandraPersistenceStore implements PersistenceStore {

	private static final String COLUMN_KEY = "key";
	private static final String COLUMN_VALUE = "value";
	public static final String TABLE_NAME = "retel";

	private final CqlSession session;

	CassandraPersistenceStore(final CqlSession session) {
		this.session = session;
	}

	public static CassandraPersistenceStore create() {
		return new CassandraPersistenceStore(CqlSession.builder().build());
	}

	@Override
	public Future<Option<String>> get(final String key) {
		return Future.of(() -> {
			final PreparedStatement statement = session
					.prepare("SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_KEY + " = ?");
			final Option<Row> result = Option.of(session.execute(statement.bind(key)).one());

			return result
					.map(r -> r.getString(COLUMN_VALUE));
		});
	}

	@Override
	public Future<String> set(final String key, final String value) {
		return Future.of(() -> {
			final PreparedStatement statement = session
					.prepare(format("INSERT INTO %s(%s, %s) VALUES (?, ?)", TABLE_NAME, COLUMN_KEY, COLUMN_VALUE));
			session.execute(statement.bind(key, value));
			return key;
		});
	}

	@Override
	public void shutdown() {
		session.close();
	}
}
