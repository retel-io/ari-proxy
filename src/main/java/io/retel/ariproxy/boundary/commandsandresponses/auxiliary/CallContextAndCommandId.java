package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CallContextAndCommandId {

	private final String callContext;
	private final String commandId;

	public CallContextAndCommandId(String callContext, String commandId) {
		this.callContext = callContext;
		this.commandId = commandId;
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
