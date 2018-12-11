package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AriMessageEnvelope {

	private AriMessageType type;
	private String commandsTopic;
	private Object payload;
	private String resourceId;
	private String commandId;

	public AriMessageEnvelope() {
	}

	public AriMessageEnvelope(AriMessageType type, String commandsTopic, Object payload, String resourceId,
			String commandId) {
		this.commandsTopic = commandsTopic;
		this.payload = payload;
		this.resourceId = resourceId;
		this.type = type;
		this.commandId = commandId;
	}


	public AriMessageEnvelope(AriMessageType type, String commandsTopic, Object payload, String resourceId) {
		this(type, commandsTopic, payload, resourceId, null);
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

	public String getResourceId() {
		return resourceId;
	}

	public String getCommandId() {
		return commandId;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
