package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.Value;
import io.vavr.control.Option;
import java.util.function.Function;

public class AriCommand {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private String method = null;
  private String url = null;
  private JsonNode body = null;

  public AriCommand() {}

  public AriCommand(final String method, final String url, final JsonNode body) {
    this.method = method;
    this.url = url;
    this.body = body;
  }

  public String getMethod() {
    return method;
  }

  public String getUrl() {
    return url;
  }

  public JsonNode getBody() {
    return body;
  }

  public AriCommandType extractCommandType() {
    return AriCommandType.fromRequestUri(getUrl());
  }

  public Option<AriCommandResource> extractResource() {
    final AriCommandType type = extractCommandType();
    if (type == AriCommandType.UNKNOWN) {
      return Option.none();
    }

    final Option<String> maybeResourceId =
        type.extractResourceIdFromUri(getUrl())
            .orElse(
                () -> {
                  try {
                    return type.extractResourceIdFromBody(
                            OBJECT_MAPPER.writeValueAsString(getBody()))
                        .map(Value::toOption)
                        .flatMap(Function.identity());
                  } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Unable to deserialize json", e);
                  }
                });

    return maybeResourceId.flatMap(
        resourceId -> Option.some(new AriCommandResource(type.getResourceType(), resourceId)));
  }

  @Override
  public String toString() {
    return reflectionToString(this, SHORT_PREFIX_STYLE);
  }
}
