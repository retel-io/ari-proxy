package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import io.vavr.Tuple2;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface CommandResponseHandler {
	CompletionStage<HttpResponse> apply(Tuple2<HttpRequest, CallContextAndCommandRequestContext> placeholder);
}
