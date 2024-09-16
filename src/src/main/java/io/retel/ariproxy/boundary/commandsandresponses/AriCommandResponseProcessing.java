package io.retel.ariproxy.boundary.commandsandresponses;

import akka.Done;
import akka.actor.typed.ActorRef;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResource;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResourceRelation;
import io.vavr.control.Option;
import io.vavr.control.Try;

public class AriCommandResponseProcessing {

  public static Try<Done> registerCallContext(
      final ActorRef<CallContextProviderMessage> callContextProvider,
      final String callContext,
      final AriCommand ariCommand) {

    if (!ariCommand.isCreationCommand()) {
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

    callContextProvider.tell(new RegisterCallContext(resource.getId(), callContext));
    return Try.success(Done.done());
  }
}
