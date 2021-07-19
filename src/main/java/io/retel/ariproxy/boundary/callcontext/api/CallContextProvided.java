package io.retel.ariproxy.boundary.callcontext.api;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CallContextProvided implements ProvideCallContextResponse {

  private final String callContext;

  public CallContextProvided(final String callContext) {
    this.callContext = callContext;
  }

  public String callContext() {
    return callContext;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
