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
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.health.api.ProvideHealthReport;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CallContextProviderTest {

	private final String TEST_SYSTEM = this.getClass().getSimpleName();
	private static final long TIMEOUT = 500;
	private ActorSystem system;

	private static final String RESOURCE_ID = "resourceId";
	private static final String CALL_CONTEXT = "callContext";

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
				final TestKit metricsService = new TestKit(system);
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(metricsService.getRef()));
				final RegisterCallContext request = new RegisterCallContext(RESOURCE_ID, CALL_CONTEXT);

				callContextProvider.tell(request, getRef());

				final CallContextRegistered callContextRegistered = expectMsgClass(CallContextRegistered.class);

				assertThat(callContextRegistered.resourceId(), is(RESOURCE_ID));
				assertThat(callContextRegistered.callContext(), is(CALL_CONTEXT));
			}
		};
	}

	@Test
	void verifyCreateIfMissingPolicyIsAppliedProperly() {
		new TestKit(system) {
			{
				final TestKit metricsService = new TestKit(system);
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(metricsService.getRef()));

				watch(callContextProvider);

				final ProvideCallContext request = new ProvideCallContext(RESOURCE_ID,
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
				final TestKit metricsService = new TestKit(system);
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(metricsService.getRef()));

				final ProvideCallContext request = new ProvideCallContext(RESOURCE_ID, ProviderPolicy.LOOKUP_ONLY);

				callContextProvider.tell(request, getRef());

				final Failure failure = expectMsgClass(Duration.ofMillis(TIMEOUT), Failure.class);

				assertThat(failure.cause(), instanceOf(CallContextLookupError.class));
			}
		};
	}

	@Test
	void verifyLookupOnlyPolicyIsAppliedProperlyIfEntryAlreadyExisted() {
		new TestKit(system) {
			{
				final TestKit metricsService = new TestKit(system);
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(metricsService.getRef()));

				final ProvideCallContext request = new ProvideCallContext(RESOURCE_ID,
						ProviderPolicy.CREATE_IF_MISSING);

				callContextProvider.tell(request, getRef());

				final CallContextProvided createdCallContext = expectMsgClass(Duration.ofMillis(TIMEOUT),
						CallContextProvided.class);

				callContextProvider.tell(new ProvideCallContext(RESOURCE_ID, ProviderPolicy.LOOKUP_ONLY), getRef());

				assertThat(
						expectMsgClass(Duration.ofMillis(TIMEOUT), CallContextProvided.class).callContext(),
						is(createdCallContext.callContext()));
			}
		};
	}


	@Test
	void aFailedFutureIsReceivedWhenNoCallContextExists() {
		new TestKit(system) {
			{
				final TestKit metricsService = new TestKit(system);
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(metricsService.getRef()));

				callContextProvider.tell(new ProvideCallContext(RESOURCE_ID, ProviderPolicy.LOOKUP_ONLY), getRef());

				final Failure failure = expectMsgClass(Duration.ofMillis(TIMEOUT), Failure.class);

				assertThat(failure.cause(), instanceOf(CallContextLookupError.class));
			}
		};
	}

	@Test
	void ensureHealthReportIsGeneratedOnRequest() {
		new TestKit(system) {
			{
				final TestKit metricsService = new TestKit(system);
				final ActorRef callContextProvider = system.actorOf(CallContextProvider.props(metricsService.getRef()));

				callContextProvider.tell(ProvideHealthReport.getInstance(), getRef());

				final HealthReport healthReport = expectMsgClass(HealthReport.class);

				assertThat(healthReport.errors().size(), is(0));
			}
		};
	}
}
