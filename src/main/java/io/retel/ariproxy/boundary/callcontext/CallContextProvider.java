package io.retel.ariproxy.boundary.callcontext;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import io.retel.ariproxy.akkajavainterop.CustomFutureConverters;
import io.retel.ariproxy.boundary.callcontext.api.CallContextLookupError;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.CallContextRegistered;
import io.retel.ariproxy.boundary.callcontext.api.Metrics;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProvideMetrics;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.callcontext.internal.CallContextState;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.health.api.ProvideHealthReport;
import io.retel.ariproxy.health.api.ProvideMonitoring;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import java.util.UUID;

public class CallContextProvider extends AbstractLoggingActor {

	public static final String ACTOR_NAME = CallContextProvider.class.getSimpleName();
	private CallContextState state;

	public static Props props(int maxSize, long expirationMillis) {
		return Props.create(CallContextProvider.class, maxSize, expirationMillis);
	}

	private CallContextProvider(int maxSize, long expirationMillis) {
		this.state = new CallContextState(maxSize, expirationMillis);
	}

	@Override
	public void preStart() throws Exception {
		getContext().getSystem().eventStream().publish(new ProvideMonitoring(ACTOR_NAME, self()));
		super.preStart();
	}

	public Receive createReceive() {
		return ReceiveBuilder.create()
				.match(RegisterCallContext.class, this::registerCallContextHandler)
				.match(ProvideCallContext.class, this::provideCallContextHandler)
				.match(ProvideMetrics.class, this::provideMetricsHandler)
				.match(ProvideHealthReport.class, this::provideHealthReportHandler)
				.build();
	}

	private void registerCallContextHandler(RegisterCallContext cmd) {
		log().debug("Got command: {}", cmd);
		sender().tell(registerCallContext(cmd.resourceId(), cmd.callContext()), self());
	}

	private void provideCallContextHandler(ProvideCallContext cmd) {
		log().debug("Got command: {}", cmd);
		final Option<String> maybeCallContext = state.get(cmd.callContext());
		// NOTE: No combinators, as we need to stay in the current thread!
		if (maybeCallContext.isDefined()) {
			replyWithCallContext(maybeCallContext.get());
		} else {
			if (ProviderPolicy.CREATE_IF_MISSING.equals(cmd.policy())) {
				final String callContext = UUID.randomUUID().toString();
				registerCallContext(cmd.callContext(), callContext);
				replyWithCallContext(callContext);
			} else {
				replyWithFailure(new CallContextLookupError("Failed to lookup call context..."));
			}
		}
	}

	private void provideHealthReportHandler(ProvideHealthReport cmd) {
		final HealthReport report = state.currentSize() > state.maxSize() * 0.9
				? HealthReport.error(String.format(
									"callcontext store size %d is above threshold %f",
									state.currentSize(), state.maxSize() * 0.9
							))
				: HealthReport.ok();

		sender().tell(report, self());
	}

	private void provideMetricsHandler(ProvideMetrics cmd) {
		sender().tell(
				new Metrics(state.maxSize(), state.currentSize()),
				self()
		);
	}

	private void replyWithCallContext(final String callContext) {
		sender().tell(new CallContextProvided(callContext), self());
	}

	private void replyWithFailure(Throwable t) {
		Patterns.pipe(
				CustomFutureConverters.toScala(Future.failed(t)),
				context().dispatcher()
		).to(sender());
	}

	private CallContextRegistered registerCallContext(String resourceId, String callContext) {
		log().debug("Going to register resourceId '{}' => callContext '{}'", resourceId, callContext);
		state.update(resourceId, callContext);
		return new CallContextRegistered(resourceId, callContext);
	}
}
