package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AriMessageEnvelope {

	private final AriMessageType type;
	private final String commandsTopic;
	private final Object payload;
	private final String callContext;
	private final String commandId;
	private final CommandRequest commandRequest;

	public AriMessageEnvelope(AriMessageType type, String commandsTopic, Object payload, String callContext, String commandId, CommandRequest commandRequest) {
		this.commandsTopic = commandsTopic;
		this.payload = payload;
		this.callContext = callContext;
		this.type = type;
		this.commandId = commandId;
		this.commandRequest = commandRequest;
	}

	public AriMessageEnvelope(AriMessageType type, String commandsTopic, Object payload, String callContext) {
		this(type, commandsTopic, payload, callContext, null, null);
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

	public CommandRequest getCommandRequest() { return commandRequest; }

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
