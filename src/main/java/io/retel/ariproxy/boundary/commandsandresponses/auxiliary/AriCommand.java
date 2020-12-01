package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.Value;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class AriCommand {
  private final String method;
  private final String url;
  private final JsonNode body;

  @JsonCreator
  public AriCommand(
      @JsonProperty("method") final String method,
      @JsonProperty("url") final String url,
      @JsonProperty("body") final JsonNode body) {
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

  public List<AriResource> extractResources() {
    List<AriResource> ariResources = AriCommandType.extractAllResources(getUrl());

    final AriCommandType commandType = extractCommandType();
    final Option<AriResource> resourceFromBody =
        commandType
            .extractResourceIdFromBody(getBody().toString())
            .flatMap(Value::toOption)
            .map(resourceId -> new AriResource(commandType.getResourceType(), resourceId));
    if (resourceFromBody.isDefined() && !ariResources.contains(resourceFromBody.get())) {
      ariResources = ariResources.push(resourceFromBody.get());
    }

    return ariResources;
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
