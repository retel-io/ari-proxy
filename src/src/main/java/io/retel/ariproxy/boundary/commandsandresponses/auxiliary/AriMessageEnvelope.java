package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import java.util.List;

public class AriMessageEnvelope {
  private final AriMessageType type;
  private final String commandsTopic;
  private final Object payload;
  private final String callContext;
  private final List<AriResource> resources;
  private final String commandId;
  private final CommandRequest commandRequest;

  public AriMessageEnvelope(
      final AriMessageType type,
      final String commandsTopic,
      final Object payload,
      final String callContext,
      final List<AriResource> resources,
      final String commandId,
      final CommandRequest commandRequest) {
    this.commandsTopic = commandsTopic;
    this.payload = payload;
    this.callContext = callContext;
    this.type = type;
    this.resources = resources;
    this.commandId = commandId;
    this.commandRequest = commandRequest;
  }

  public AriMessageEnvelope(
      final AriMessageType type,
      final String commandsTopic,
      final Object payload,
      final String callContext,
      final String commandId,
      final CommandRequest commandRequest) {
    this(type, commandsTopic, payload, callContext, null, commandId, commandRequest);
  }

  public AriMessageEnvelope(
      final AriMessageType type,
      final String commandsTopic,
      final Object payload,
      final String callContext,
      final List<AriResource> resources) {
    this(type, commandsTopic, payload, callContext, resources, null, null);
  }

  public AriMessageEnvelope(
      final AriMessageType type,
      final String commandsTopic,
      final Object payload,
      final String callContext) {
    this(type, commandsTopic, payload, callContext, null, null, null);
  }

  public AriMessageType getType() {
    return type;
  }

  public String getCommandsTopic() {
    return commandsTopic;
  }

  public Object getPayload() {
    return payload;
  }

  public String getCallContext() {
    return callContext;
  }

  public List<AriResource> getResources() {
    return resources;
  }

  public String getCommandId() {
    return commandId;
  }

  public CommandRequest getCommandRequest() {
    return commandRequest;
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
