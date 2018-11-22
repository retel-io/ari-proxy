package io.retel.ariproxy;

import akka.actor.OneForOneStrategy;
import akka.actor.SupervisorStrategy;
import akka.actor.SupervisorStrategy.Directive;
import akka.actor.SupervisorStrategyConfigurator;
import akka.japi.pf.PFBuilder;
import scala.PartialFunction;

public class GuardianSupervisorStrategy implements SupervisorStrategyConfigurator {

	@Override
	public SupervisorStrategy create() {
		final PartialFunction<Throwable, Directive> decider = new PFBuilder<Throwable, Directive>()
				.matchAny(t -> SupervisorStrategy.restart())
				.build();
		return new OneForOneStrategy(decider);
	}
}
