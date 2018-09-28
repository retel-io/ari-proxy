package io.retel.ariproxy.akkajavainterop;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import io.vavr.concurrent.Future;

public class PatternsAdapter {

	@SuppressWarnings("unchecked")
	public static <T> Future<T> ask(final ActorRef actor, final Object message, final long timeoutMillis) {
		return CustomFutureConverters.fromScala(Patterns.ask(
				actor,
				message,
				timeoutMillis
		))
				.map(o -> (T) o);
	}
}
