package io.retel.ariproxy.metrics;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import io.retel.ariproxy.metrics.api.MetricRegistered;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {
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

	@Test()
	void increaseCounterMessageIsRespondedProperly() {
		new TestKit(system) {
			{
				final ActorRef metricsService = system.actorOf(MetricsService.props());
				metricsService.tell(
						new IncreaseCounter("CALL CONTEXT"), getRef());

				expectMsg(MetricRegistered.COUNTER_INCREASED);
			}
		};
	}

	@Test()
	void startCallSetupTimerMessageIsRespondedProperly() {
		new TestKit(system) {
			{
				final ActorRef metricsService = system.actorOf(MetricsService.props());
				metricsService.tell(
						new StartCallSetupTimer("CALL CONTEXT"), getRef());

				expectMsg(MetricRegistered.TIMER_STARTED);
			}
		};
	}

	@Test()
	void stopCallSetupTimerMessageIsRespondedProperly() {
		new TestKit(system) {
			{
				final ActorRef metricsService = system.actorOf(MetricsService.props());
				metricsService.tell(
						new StartCallSetupTimer("CALL CONTEXT"), getRef());
				expectMsg(MetricRegistered.TIMER_STARTED);
				metricsService.tell(
						new StopCallSetupTimer("CALL CONTEXT","THE APP NAME"), getRef());

				expectMsg(MetricRegistered.TIMER_STOPPED);
			}
		};
	}
}
