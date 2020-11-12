package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import java.util.Optional;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

public class AriCommandResource {
  private final AriCommandType type;
  private final String id;

  AriCommandResource(final AriCommandType type) {
    this.type = type;
    this.id = null;
  }

  AriCommandResource(final AriCommandType type, final String id) {
    this.type = type;
    this.id = id;
  }

  public AriCommandType getType() {
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
