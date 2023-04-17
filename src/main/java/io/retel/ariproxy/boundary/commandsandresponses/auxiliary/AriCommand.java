package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.Value;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class AriCommand {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    final String uri = getUrl().split("\\?")[0];
    return AriCommandType.fromRequestUri(uri);
  }

  public List<AriResourceRelation> extractResourceRelations() {

    final String uri = getUrl().split("\\?")[0];
    List<AriResource> ariResources = AriCommandType.extractAllResources(uri);

    final AriCommandType commandType = AriCommandType.fromRequestUri(uri);

    if (!commandType.isRouteForResourceCreation()) {
      return ariResources.map(r -> new AriResourceRelation(r, false));
    }

    final Option<AriResource> createdResourceFromUri =
        ariResources.find(resource -> resource.getType().equals(commandType.getResourceType()));

    try {
      final Option<AriResource> createdResourceFromBody =
          commandType
              .extractResourceIdFromBody(OBJECT_MAPPER.writeValueAsString(getBody()))
              .flatMap(Value::toOption)
              .map(resourceId -> new AriResource(commandType.getResourceType(), resourceId));

      final Option<AriResource> createdResource =
          createdResourceFromUri.orElse(createdResourceFromBody);

      if (createdResource.isDefined() && !ariResources.contains(createdResource.get())) {
        ariResources = ariResources.push(createdResource.get());
      }

      return ariResources.map(
          ariResource ->
              new AriResourceRelation(
                  ariResource, createdResource.map(r -> r.equals(ariResource)).getOrElse(false)));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize command body: " + this, e);
    }
  }

  public boolean isCreationCommand() {
    return extractCommandType().isRouteForResourceCreation() && "POST".equals(method);
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
