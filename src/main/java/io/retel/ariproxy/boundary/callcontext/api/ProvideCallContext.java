package io.retel.ariproxy.boundary.callcontext.api;

import io.vavr.control.Option;
import java.io.Serializable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ProvideCallContext implements Serializable {

  private final String resourceId;
  private final ProviderPolicy policy;
  private final Option<String> maybeCallContextFromChannelVars;

  public ProvideCallContext(
      String resourceId,
      final Option<String> maybeCallContextFromChannelVars,
      ProviderPolicy policy) {
    this.resourceId = resourceId;
    this.maybeCallContextFromChannelVars = maybeCallContextFromChannelVars;
    this.policy = policy;
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

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
