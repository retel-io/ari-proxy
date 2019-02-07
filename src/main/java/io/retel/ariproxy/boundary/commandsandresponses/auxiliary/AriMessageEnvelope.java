package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AriMessageEnvelope {

	private AriMessageType type;
	private String commandsTopic;
	private Object payload;
	private String callContext;
	private String commandId;

	public AriMessageEnvelope() {
	}

	public AriMessageEnvelope(AriMessageType type, String commandsTopic, Object payload, String callContext, String commandId) {
		this.commandsTopic = commandsTopic;
		this.payload = payload;
		this.callContext = callContext;
		this.type = type;
		this.commandId = commandId;
	}


	public AriMessageEnvelope(AriMessageType type, String commandsTopic, Object payload, String callContext) {
		this(type, commandsTopic, payload, callContext, null);
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

	public String getCommandId() {
		return commandId;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
