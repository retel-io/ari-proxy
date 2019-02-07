package io.retel.ariproxy.boundary.callcontext;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status.Failure;
import akka.testkit.javadsl.TestKit;
import io.retel.ariproxy.boundary.callcontext.api.CallContextLookupError;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.CallContextRegistered;
import io.retel.ariproxy.boundary.callcontext.api.Metrics;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProvideMetrics;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.health.api.ProvideHealthReport;
import io.vavr.collection.Stream;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CallContextProviderTest {

	private final String TEST_SYSTEM = this.getClass().getSimpleName();
	private static final long GENEROUS_TIMEOUT = 500;
	private static final long SMALL_EXPIRATION_TIME = 200;
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
	void verifyRegisterCallContextReturnsThatContext() {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						23
				));

				final RegisterCallContext request = new RegisterCallContext("callContext", "callContext");

				callContextProvider.tell(request, getRef());

				final CallContextRegistered callContextRegistered = expectMsgClass(CallContextRegistered.class);

				assertThat(callContextRegistered.resourceId(), is("callContext"));
				assertThat(callContextRegistered.callContext(), is("callContext"));
			}
		};
	}

	@Test
	void verifyCreateIfMissingPolicyIsAppliedProperly() {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						23
				));

				watch(callContextProvider);

				final ProvideCallContext request = new ProvideCallContext("callContext",
						ProviderPolicy.CREATE_IF_MISSING);

				callContextProvider.tell(request, getRef());

				final CallContextProvided callContextProvided = expectMsgClass(CallContextProvided.class);

				assertThat(Try.of(() -> UUID.fromString(callContextProvided.callContext())).isSuccess(), is(true));
			}
		};
	}

	@Test
	void verifyLookupOnlyPolicyIsAppliedProperly() {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						23
				));

				final ProvideCallContext request = new ProvideCallContext("callContext", ProviderPolicy.LOOKUP_ONLY);

				callContextProvider.tell(request, getRef());

				final Failure failure = expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), Failure.class);

				assertThat(failure.cause(), instanceOf(CallContextLookupError.class));
			}
		};
	}

	@Test
	void verifyLookupOnlyPolicyIsAppliedProperlyIfEntryAlreadyExisted() {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						1000
				));

				final ProvideCallContext request = new ProvideCallContext("callContext",
						ProviderPolicy.CREATE_IF_MISSING);

				callContextProvider.tell(request, getRef());

				final CallContextProvided createdCallContext = expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT),
						CallContextProvided.class);

				callContextProvider.tell(new ProvideCallContext("callContext", ProviderPolicy.LOOKUP_ONLY), getRef());

				assertThat(
						expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), CallContextProvided.class).callContext(),
						is(createdCallContext.callContext()));
			}
		};
	}


	@Test
	void aFailedFutureIsReceivedWhenNoCallContextExists() {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						1000
				));

				callContextProvider.tell(new ProvideCallContext("callContext", ProviderPolicy.LOOKUP_ONLY), getRef());

				final Failure failure = expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), Failure.class);

				assertThat(failure.cause(), instanceOf(CallContextLookupError.class));
			}
		};
	}

	@Test
	void callContextExpiresAfterAGivenAmountOfTimePassed() throws InterruptedException {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						SMALL_EXPIRATION_TIME
				));

				final RegisterCallContext request = new RegisterCallContext("callContext", "callContext");

				send(callContextProvider, request);

				assertThat(
						expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), CallContextRegistered.class).callContext(),
						is("callContext"));

				send(callContextProvider, new ProvideCallContext("callContext", ProviderPolicy.LOOKUP_ONLY));

				assertThat(
						expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), CallContextProvided.class).callContext(),
						is("callContext"));

				Thread.sleep(SMALL_EXPIRATION_TIME + 42);

				send(callContextProvider, new ProvideCallContext("callContext", ProviderPolicy.LOOKUP_ONLY));

				final Failure failure = expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), Failure.class);

				assertThat(failure.cause(), instanceOf(CallContextLookupError.class));

				expectNoMessage(Duration.ofMillis(GENEROUS_TIMEOUT));
			}
		};
	}

	@Test
	void oldestCallContextIsDroppedIfMaxSizeIsExceeded() {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						10000
				));

				final RegisterCallContext request1 = new RegisterCallContext("resourceId1", "callContext1");
				final RegisterCallContext request2 = new RegisterCallContext("resourceId2", "callContext2");

				callContextProvider.tell(request1, getRef());

				assertThat(
						expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), CallContextRegistered.class).callContext(),
						is("callContext1"));

				callContextProvider.tell(request2, getRef());

				assertThat(
						expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), CallContextRegistered.class).callContext(),
						is("callContext2"));

				callContextProvider.tell(new ProvideCallContext("resourceId1", ProviderPolicy.LOOKUP_ONLY), getRef());

				final Failure failure = expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), Failure.class);

				assertThat(failure.cause(), instanceOf(CallContextLookupError.class));

				callContextProvider.tell(new ProvideCallContext("resourceId2", ProviderPolicy.LOOKUP_ONLY), getRef());

				assertThat(
						expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), CallContextProvided.class).callContext(),
						is("callContext2"));
			}
		};
	}

	@ParameterizedTest
	@CsvSource({
			"23, 3",
			"5, 0",
			"5, 10"
	})
	void ensureProvideMetricsAlwaysReturnsAccureValues(int maxSize, int amountOfEntries) {
		new TestKit(system) {
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						maxSize,
						100000
				));

				watch(callContextProvider);

				Stream.range(0, amountOfEntries)
						.map(id -> new RegisterCallContext(
								String.format("callContext%s", id),
								String.format("callContext%s", id))
						)
						.forEach(registerCommand -> {
							callContextProvider.tell(registerCommand, getRef());
							assertThat(
									expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), CallContextRegistered.class)
											.callContext(),
									is(registerCommand.callContext()));
						});

				callContextProvider.tell(ProvideMetrics.instance(), getRef());

				final Metrics metrics = expectMsgClass(Duration.ofMillis(GENEROUS_TIMEOUT), Metrics.class);

				assertThat(metrics.maxSize(), is(maxSize));
				assertThat(metrics.currentSize(), is(Math.min(amountOfEntries, maxSize)));
			}
		};
	}

	@Test
	void ensureOkHealthReportIsGeneratedIfThresholdIsNotExceeded() {
		new TestKit(system)
		{
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						10_000
				));

				callContextProvider.tell(ProvideHealthReport.getInstance(), getRef());
				HealthReport healthReport = expectMsgClass(HealthReport.class);
				assertThat(healthReport.errors().size(), is(0));
			}
		};
	}

	@Test
	void ensureNotOkHealthReportIsGeneratedIfThresholdIsExceeded() {
		new TestKit(system)
		{
			{
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(
						1,
						10_000
				));

				watch(callContextProvider);

				callContextProvider.tell(new RegisterCallContext("callContext", "callContext"), getRef());
				expectMsgClass(CallContextRegistered.class); // Note: We don't care about the details here

				callContextProvider.tell(ProvideHealthReport.getInstance(), getRef());
				final HealthReport healthReport = expectMsgClass(HealthReport.class);
				assertThat(healthReport.errors().size(), is(1));
			}
		};
	}
}
