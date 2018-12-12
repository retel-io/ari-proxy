package io.retel.ariproxy.boundary.commandsandresponses;

import static io.vavr.API.Some;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.retel.ariproxy.akkajavainterop.PatternsAdapter;
import io.retel.ariproxy.boundary.callcontext.api.CallContextRegistered;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommandType;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

public class AriCommandResponseProcessing {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final ObjectWriter genericWriter = mapper.writer();

	public static Either<RuntimeException, Runnable> registerCallContext(
			final ActorRef callContextProvider,
			final String callContext,
			final AriCommand ariCommand) {

		final String uri = ariCommand.getUrl();
		final JsonNode body = ariCommand.getBody();

		final String bodyJson = Option.of(body)
				.map(value -> Try.of(() -> genericWriter.writeValueAsString(value)))
				.getOrElse(Try.success(""))
				.getOrElseThrow(t -> new RuntimeException(t));

		final AriCommandType type = AriCommandType.fromRequestUri(uri);

		return type
				.extractResourceIdFromUri(uri)
				.flatMap(resourceIdTry -> resourceIdTry.isFailure() ? type.extractResourceIdFromBody(bodyJson) : Some(resourceIdTry))
				.map(resourceIdTry -> resourceIdTry.map(resourceId -> (Runnable) () -> {
					PatternsAdapter.<CallContextRegistered>ask(
							callContextProvider,
							new RegisterCallContext(resourceId, callContext),
							100
					)
							.await(); // Note: We need to wait for the registration to finish before we can continue

				}))
				.getOrElse(Try.success(() -> {}))
				.toEither()
				.mapLeft(cause -> new RuntimeException(
						String.format(
								"Failed to extract resourceId from both uri='%s' and body='%s'",
								uri,
								bodyJson
						),
						cause
				));
	}
}
