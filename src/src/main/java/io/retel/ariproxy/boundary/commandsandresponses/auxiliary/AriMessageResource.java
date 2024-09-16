package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

public class AriMessageResource {
  final ResourceType type;
  final String id;

  public AriMessageResource(final ResourceType type, final String id) {
    this.type = type;
    this.id = id;
  }

  public ResourceType getType() {
    return type;
  }

  public String getId() {
    return id;
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

  enum ResourceType {
    CHANNEL,
    BRIDGE,
    PLAYBACK,
    RECORDING,
    SNOOPING
  }
}
