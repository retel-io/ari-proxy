package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AriMessageEnvelope {

	private AriMessageType type;
	private String commandsTopic;
	private String payload;
	private String resourceId;

	public AriMessageEnvelope() {}

	public AriMessageEnvelope(AriMessageType type, String commandsTopic, String payload, String resourceId) {
		this.commandsTopic = commandsTopic;
		this.payload = payload;
		this.resourceId = resourceId;
		this.type = type;
	}

	public AriMessageType getType() {
		return type;
	}

	public String getCommandsTopic() {
		return commandsTopic;
	}

	public String getPayload() {
		return payload;
	}

	public String getResourceId() {
		return resourceId;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
