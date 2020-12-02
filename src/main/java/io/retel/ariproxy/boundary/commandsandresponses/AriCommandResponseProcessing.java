package io.retel.ariproxy.boundary.commandsandresponses;

import akka.Done;
import akka.actor.ActorRef;
import io.retel.ariproxy.akkajavainterop.PatternsAdapter;
import io.retel.ariproxy.boundary.callcontext.api.CallContextRegistered;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResource;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResourceRelation;
import io.vavr.control.Option;
import io.vavr.control.Try;

public class AriCommandResponseProcessing {

  public static Try<Done> registerCallContext(
      final ActorRef callContextProvider, final String callContext, final AriCommand ariCommand) {

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
          PatternsAdapter.<CallContextRegistered>ask(
                  callContextProvider, new RegisterCallContext(resource.getId(), callContext), 100)
              .await();
          return Done.done();
        });
  }
}
