package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import java.util.Optional;

public class AriCommandResource {
  private final AriResourceType type;
  private final String id;

  AriCommandResource(final AriResourceType type) {
    this.type = type;
    this.id = null;
  }

  AriCommandResource(final AriResourceType type, final String id) {
    this.type = type;
    this.id = id;
  }

  public AriResourceType getType() {
    return type;
  }

  public Optional<String> getId() {
    return Optional.ofNullable(id);
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
