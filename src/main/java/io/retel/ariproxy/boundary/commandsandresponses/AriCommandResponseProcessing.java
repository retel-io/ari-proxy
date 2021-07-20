package io.retel.ariproxy.boundary.commandsandresponses;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.callcontext.api.CallContextRegistered;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResource;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResourceRelation;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AriCommandResponseProcessing {

  private static final Logger LOGGER = LoggerFactory.getLogger(AriCommandResponseProcessing.class);

  public static Try<Done> registerCallContext(
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final String callContext,
      final AriCommand ariCommand,
      final ActorSystem<?> system) {

    if (!ariCommand.extractCommandType().isCreationCommand()) {
      return Try.success(Done.done());
    }

    final Option<AriResource> maybeResource =
        ariCommand
            .extractResourceRelations()
            .find(AriResourceRelation::isCreated)
            .map(AriResourceRelation::getResource);

    if (maybeResource.isEmpty()) {
      return Try.failure(
          new RuntimeException(
              String.format(
                  "Failed to extract resourceId from command '%s'", ariCommand.toString())));
    }

    final AriResource resource = maybeResource.get();

    return Try.of(
            () -> {
              AskPattern.<CallContextProviderMessage, CallContextRegistered>ask(
                      callContextProvider,
                      replyTo -> new RegisterCallContext(resource.getId(), callContext, replyTo),
                      Duration.ofMillis(100),
                      system.scheduler())
                  .toCompletableFuture()
                  .get();

              return Done.done();
            })
        .onFailure(
            error -> {
              LOGGER.error(
                  "Registering callContext {} for resource {} failed",
                  callContext,
                  resource.getId(),
                  error);
            });
  }
}
