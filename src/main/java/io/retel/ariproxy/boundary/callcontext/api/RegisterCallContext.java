package io.retel.ariproxy.boundary.callcontext.api;

import akka.actor.typed.ActorRef;
import java.io.Serializable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class RegisterCallContext implements CallContextProviderMessage, Serializable {

  private final String resourceId;
  private final String callContext;
  private final ActorRef<CallContextRegistered> replyTo;

  @Deprecated
  public RegisterCallContext(String resourceId, String callContext) {
    this(resourceId, callContext, null);
  }

  public RegisterCallContext(
      final String resourceId,
      final String callContext,
      final ActorRef<CallContextRegistered> replyTo) {
    this.resourceId = resourceId;
    this.callContext = callContext;
    this.replyTo = replyTo;
  }

  public String resourceId() {
    return resourceId;
  }

  public String callContext() {
    return callContext;
  }

  public ActorRef<CallContextRegistered> replyTo() {
    return replyTo;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
