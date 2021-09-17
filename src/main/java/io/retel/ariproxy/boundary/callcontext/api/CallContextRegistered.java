package io.retel.ariproxy.boundary.callcontext.api;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

public class CallContextRegistered {

  private final String resourceId;
  private final String callContext;

  public CallContextRegistered(String resourceId, String callContext) {
    this.resourceId = resourceId;
    this.callContext = callContext;
  }

  public String resourceId() {
    return resourceId;
  }

  public String callContext() {
    return callContext;
  }

  @Override
  public String toString() {
    return reflectionToString(this, SHORT_PREFIX_STYLE);
  }

  @Override
  public boolean equals(final Object o) {
    return reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return reflectionHashCode(this);
  }
}
