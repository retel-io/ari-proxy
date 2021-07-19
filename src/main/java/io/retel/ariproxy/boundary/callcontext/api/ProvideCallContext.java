package io.retel.ariproxy.boundary.callcontext.api;

import akka.actor.typed.ActorRef;
import io.vavr.control.Option;
import java.io.Serializable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ProvideCallContext implements CallContextProviderMessage, Serializable {

  private final String resourceId;
  private final ProviderPolicy policy;
  private final Option<String> maybeCallContextFromChannelVars;
  private final ActorRef<ProvideCallContextResponse> replyTo;

  @Deprecated
  public ProvideCallContext(
      final String resourceId,
      final Option<String> maybeCallContextFromChannelVars,
      final ProviderPolicy policy) {
    this(resourceId, policy, maybeCallContextFromChannelVars, null);
  }

  public ProvideCallContext(
      final String resourceId,
      final ProviderPolicy policy,
      final Option<String> maybeCallContextFromChannelVars,
      final ActorRef<ProvideCallContextResponse> replyTo) {
    this.resourceId = resourceId;
    this.policy = policy;
    this.maybeCallContextFromChannelVars = maybeCallContextFromChannelVars;
    this.replyTo = replyTo;
  }

  public String resourceId() {
    return resourceId;
  }

  public ProviderPolicy policy() {
    return policy;
  }

  public Option<String> maybeCallContextFromChannelVars() {
    return maybeCallContextFromChannelVars;
  }

  public ActorRef<ProvideCallContextResponse> replyTo() {
    return replyTo;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
