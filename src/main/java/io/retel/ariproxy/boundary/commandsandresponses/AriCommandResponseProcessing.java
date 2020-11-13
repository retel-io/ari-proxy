package io.retel.ariproxy.boundary.commandsandresponses;

import akka.actor.ActorRef;
import io.retel.ariproxy.akkajavainterop.PatternsAdapter;
import io.retel.ariproxy.boundary.callcontext.api.CallContextRegistered;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommandResource;
import io.vavr.control.Option;
import io.vavr.control.Try;

public class AriCommandResponseProcessing {

  public static Try<Void> registerCallContext(
      final ActorRef callContextProvider, final String callContext, final AriCommand ariCommand) {

    if (!ariCommand.extractCommandType().isCreationCommand()) {
      return Try.success(null);
    }

    final Option<AriCommandResource> maybeResource = ariCommand.extractResource();
    if (maybeResource.isEmpty()) {
      return Try.failure(
          new RuntimeException(
              String.format(
                  "Failed to extract resourceId from command '%s'", ariCommand.toString())));
    }

    final AriCommandResource resource = maybeResource.get();

    return Try.of(
        () -> {
          PatternsAdapter.<CallContextRegistered>ask(
                  callContextProvider,
                  new RegisterCallContext(
                      resource.getId().get(), // TODO: this should not be optional
                      callContext),
                  100)
              .await();
          return null;
        });
  }
}
