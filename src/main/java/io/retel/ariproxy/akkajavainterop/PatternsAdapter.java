package io.retel.ariproxy.akkajavainterop;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.pattern.PipeToSupport.PipeableFuture;
import io.vavr.concurrent.Future;
import scala.concurrent.ExecutionContext;

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

	public static <T> PipeableFuture<T> pipeTo(Future<T> future, ActorRef replyTo, ExecutionContext executionContext) {
		return Patterns.pipe(CustomFutureConverters.toScala(future), executionContext).to(replyTo);
	}
}
