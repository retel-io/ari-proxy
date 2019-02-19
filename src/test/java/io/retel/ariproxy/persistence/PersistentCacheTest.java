package io.retel.ariproxy.persistence;

import static io.vavr.API.None;
import static io.vavr.API.Some;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.javadsl.TestKit;
import io.retel.ariproxy.akkajavainterop.PatternsAdapter;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.RedisUpdateTimerStart;
import io.retel.ariproxy.metrics.RedisUpdateTimerStop;
import io.vavr.control.Option;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersistentCacheTest {

	private static final String KEY = "key";
	private static final String VALUE = "value";
	private final String TEST_SYSTEM = this.getClass().getSimpleName();
	private ActorSystem system;

	@AfterEach
	void teardown() {
		TestKit.shutdownActorSystem(system);
		system.terminate();
	}

	@BeforeEach
	void setup() {
		system = ActorSystem.create(TEST_SYSTEM);
	}

	@Test
	void queryReturnsNone() {
		final TestKit probe = new TestKit(system);
		final TestKit metricsService = new TestKit(system);
		final ActorRef cache = system.actorOf(Props.create(Cache.class, metricsService.getRef()), "cache");

		probe.send(cache, new QueryCache(KEY));

		final Option result = probe.expectMsgClass(Option.class);

		assertThat(result, is(None()));

		metricsService.expectMsgClass(Duration.ofMillis(100), IncreaseCounter.class);
	}

	@Test
	void queryReturnsExpectedValue() {
		final TestKit probe = new TestKit(system);
		final TestKit metricsService = new TestKit(system);
		final ActorRef cache = system.actorOf(Props.create(Cache.class, metricsService.getRef()), "cache");

		probe.send(cache, new UpdateCache(KEY, VALUE));

		final SetDone setDone = probe.expectMsgClass(SetDone.class);

		assertThat(setDone.getKey(), is(cache.path().name() + ":" + KEY));
		assertThat(setDone.getValue(), is(VALUE));

		final RedisUpdateTimerStart updateTimerStart = metricsService.expectMsgClass(RedisUpdateTimerStart.class);
		final RedisUpdateTimerStop updateTimerStop = metricsService.expectMsgClass(RedisUpdateTimerStop.class);

		assertThat(updateTimerStart.getContext(), is(updateTimerStop.getContext()));

		probe.send(cache, new QueryCache(KEY));

		final Option result = probe.expectMsgClass(Option.class);

		assertThat(result, is(Some(VALUE)));

		metricsService.expectNoMessage(Duration.ofMillis(100));
	}

	@Test
	void updateStillRecordsMetricsIfRedisSetFails() {
		final TestKit probe = new TestKit(system);
		final TestKit metricsService = new TestKit(system);
		final ActorRef cache = system.actorOf(Props.create(Cache.class, metricsService.getRef()), "cache");

		probe.send(cache, new UpdateCache("failure", VALUE));

		probe.expectMsgClass(Duration.ofMillis(100), Failure.class);

		final RedisUpdateTimerStart updateTimerStart = metricsService.expectMsgClass(RedisUpdateTimerStart.class);
		final RedisUpdateTimerStop updateTimerStop = metricsService.expectMsgClass(RedisUpdateTimerStop.class);

		assertThat(updateTimerStart.getContext(), is(updateTimerStop.getContext()));
	}
}

class Cache extends PersistentCache {

	public Cache(ActorRef metricsService) {
		super(metricsService);
	}

	@Override
	protected String keyPrefix() {
		return self().path().name();
	}

	@Override
	public Receive createReceive() {
		return ReceiveBuilder.create()
				.match(QueryCache.class,
						msg -> PatternsAdapter.pipeTo(query(msg.getKey()), sender(), context().dispatcher()))
				.match(UpdateCache.class,
						msg -> PatternsAdapter.pipeTo(
								update(msg.getKey(), msg.getValue()),
								sender(),
								context().dispatcher()))
				.matchAny(msg -> log().warning("unexpected message"))
				.build();
	}
}

class UpdateCache {

	private final String key;
	private final String value;

	UpdateCache(String key, String value) {
		this.key = key;
		this.value = value;
	}

	String getKey() {
		return key;
	}

	String getValue() {
		return value;
	}
}

class QueryCache {

	private final String key;

	QueryCache(String key) {
		this.key = key;
	}

	String getKey() {
		return key;
	}
}
