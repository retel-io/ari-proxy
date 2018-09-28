package io.retel.ariproxy.akkajavainterop;

import io.vavr.concurrent.Future;
import scala.compat.java8.FutureConverters;

public class CustomFutureConverters {

	public static <T> Future<T> fromScala(scala.concurrent.Future<T> future) {
		return Future.fromCompletableFuture(FutureConverters.toJava(future).toCompletableFuture());
	}

	public static <T> scala.concurrent.Future<T> toScala(Future<T> future) {
		return FutureConverters.toScala(future.toCompletableFuture());
	}
}
