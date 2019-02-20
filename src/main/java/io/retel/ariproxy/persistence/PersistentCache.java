package io.retel.ariproxy.persistence;

import static io.vavr.API.Some;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.MetricsService;
import io.retel.ariproxy.metrics.RedisUpdateTimerStart;
import io.retel.ariproxy.metrics.RedisUpdateTimerStop;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

public abstract class PersistentCache extends AbstractLoggingActor {

	private static final String REDIS_BACKED_CACHE_FALLBACK = "RedisBackedCacheFallback";
	private ActorRef metricsService;
	private PersistenceStore persistenceStore = null;

	private final Function<String, Future<Option<String>>> f = key -> persistenceStore.get(key)
					.andThen(done -> metricsService.tell(new IncreaseCounter(REDIS_BACKED_CACHE_FALLBACK), self()));

	private LoadingCache<String, Future<Option<String>>> cache = CacheBuilder.newBuilder()
			.expireAfterWrite(6, TimeUnit.HOURS)
			.build(CacheLoader.from(f::apply));

	private PersistentCache() {}

	protected PersistentCache(final ActorRef metricsService) {
		this.metricsService = metricsService;
	}

	abstract protected String keyPrefix();

	protected Future<SetDone> update(String key, String value) {
		final String prefixedKey = keyPrefix() + ":" + key;

		final String metricsContext = UUID.randomUUID().toString();
		metricsService.tell(new RedisUpdateTimerStart(metricsContext), self());

		cache.put(prefixedKey, Future.successful(Some(value)));
		return persistenceStore.set(prefixedKey, value).map(v -> new SetDone(prefixedKey, value))
				.andThen(done -> metricsService.tell(new RedisUpdateTimerStop(metricsContext), self()));
	}

	protected Future<Option<String>> query(String key) {
		final String prefixedKey = keyPrefix() + ":" + key;
		return Future.of(() -> cache.get(prefixedKey)).flatMap(x -> x);
	}

	@Override
	public void preStart() throws Exception {
		super.preStart();
		persistenceStore = providePersistenceStore();
		metricsService = Option.of(metricsService).getOrElse(() -> context().actorOf(MetricsService.props(), "metrics-service"));
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();
		persistenceStore.shutdown();
	}

	protected Future<HealthReport> provideHealthReport() {
		return provideHealthReport("HEALTHCHECK_" + self().path().toSerializationFormat());
	}

	protected Future<HealthReport> provideHealthReport(final String key) {
		final String testValue = StringUtils.reverse(key);

		return persistenceStore.set(key, testValue).flatMap(v -> persistenceStore.get(key)).transformValue(tryOfValue ->
				tryOfValue
						.map(maybeValue -> maybeValue.map(v -> testValue.equals(v)
								? Try.success(HealthReport.ok())
								: Try.success(HealthReport.error(String.format("RedisCheck: %s does not match expected %s", v, testValue)))
						)
						.getOrElse(() -> Try.success(HealthReport.error("RedisCheck: empty result on get()"))))
						.getOrElseGet(t -> Try.success(HealthReport.error("RedisCheck: " + t.getMessage()))));
	}

	private PersistenceStore providePersistenceStore() {

		final String persistenceStoreClassName = ConfigFactory.load().getConfig("service").getString("persistence-store");

		return Try.of(() -> Class.forName(persistenceStoreClassName))
				.flatMap(clazz -> Try.of(() -> clazz.getMethod("create")))
				.flatMap(method -> Try.of(() -> (PersistenceStore)method.invoke(null)))
				.getOrElseThrow(t -> new RuntimeException("Failed to load any PersistenceStore", t));
	}
}
