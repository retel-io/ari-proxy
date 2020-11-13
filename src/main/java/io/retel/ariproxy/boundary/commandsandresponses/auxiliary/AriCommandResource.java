package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.Value;
import io.vavr.control.Option;
import java.util.function.Function;

public class AriCommandResource {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final AriResourceType type;
  private final String id;

  AriCommandResource(final AriResourceType type, final String id) {
    this.type = type;
    this.id = id;
  }

  public static Option<AriCommandResource> ofAriCommand(final AriCommand ariCommand) {
    final AriCommandType type = ariCommand.extractCommandType();

    if (type == AriCommandType.UNKNOWN) {
      return Option.none();
    }

    final Option<String> maybeResourceId =
        type.extractResourceIdFromUri(ariCommand.getUrl())
            .orElse(
                () -> {
                  try {
                    return type.extractResourceIdFromBody(
                            OBJECT_MAPPER.writeValueAsString(ariCommand.getBody()))
                        .map(Value::toOption)
                        .flatMap(Function.identity());
                  } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Unable to deserialize json", e);
                  }
                });

    return maybeResourceId.flatMap(
        resourceId -> Option.some(new AriCommandResource(type.getResourceType(), resourceId)));
  }

  public AriResourceType getType() {
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
}
