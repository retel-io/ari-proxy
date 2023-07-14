package io.retel.ariproxy;

import akka.actor.typed.ActorRef;
import akka.pattern.StatusReply;

public class AriCommandMessage {
  private final String commandJson;
  private final ActorRef<StatusReply<Void>> replyTo;

  public AriCommandMessage(final String commandJson, final ActorRef<StatusReply<Void>> replyTo) {
    this.commandJson = commandJson;
    this.replyTo = replyTo;
  }

  public String getCommandJson() {
    return commandJson;
  }

  public ActorRef<StatusReply<Void>> getReplyTo() {
    return replyTo;
  }
}
