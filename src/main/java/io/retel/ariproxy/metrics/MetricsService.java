package io.retel.ariproxy.metrics;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.vavr.control.Option;
import java.util.HashMap;
import java.util.Map;

public class MetricsService extends AbstractLoggingActor {

	private static final String REDIS_UPDATE_DELAY = "RedisUpdateDelay";

	private Map<String, Sample> timers = new HashMap<>();
	private Map<String, Counter> counters = new HashMap<>();
	private MeterRegistry registry;

	public static Props props() {
		return Props.create(MetricsService.class);
	}

	private MetricsService() {
	}

	@Override
	public Receive createReceive() {
		return ReceiveBuilder.create()
				.match(RedisUpdateTimerStart.class, this::handleRedisUpdateStart)
				.match(RedisUpdateTimerStop.class, this::handleRedisUpdateStop)
				.match(StartCallSetupTimer.class, this::handleStart)
				.match(StopCallSetupTimer.class, this::handleStop)
				.match(IncreaseCounter.class, this::handleIncreaseCounter)
				.matchAny(msg -> log().warning("Unexpected message received {}", msg))
				.build();
	}

	@Override
	public void preStart() throws Exception {
		super.preStart();
		registry = new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);
	}

	@Override
	public void postStop() throws Exception {
		registry.close();
		super.postStop();
	}

	private void handleRedisUpdateStart(RedisUpdateTimerStart start) {
		timers.put(start.getContext(), Timer.start(registry));
		sender().tell(MetricRegistered.TIMER_STARTED, self());
	}

	private void handleRedisUpdateStop(RedisUpdateTimerStop stop) {
		Option
				.of(timers.get(stop.getContext()))
				.peek(sample -> {
					sample.stop(registry.timer(REDIS_UPDATE_DELAY));
					timers.remove(stop.getContext());
				}).toTry().onSuccess((metric) -> sender().tell(MetricRegistered.TIMER_STOPPED, self()));
	}

	private void handleIncreaseCounter(IncreaseCounter increaseCounter) {
		counters.computeIfAbsent(increaseCounter.getName(), key -> registry.counter(key)).increment();
		sender().tell(MetricRegistered.COUNTER_INCREASED, self());
	}

	private void handleStart(StartCallSetupTimer start) {
		timers.put(start.getCallContext(), Timer.start(registry));
		sender().tell(MetricRegistered.TIMER_STARTED, self());
	}

	private void handleStop(StopCallSetupTimer stop) {
		Option
				.of(timers.get(stop.getCallcontext()))
				.peek(sample -> {
					sample.stop(registry.timer("CallSetupDelay", "stasisApp", stop.getApplication()));
					timers.remove(stop.getCallcontext());
				}).toTry().onSuccess((metric) -> sender().tell(MetricRegistered.TIMER_STOPPED, self()));
	}
}
