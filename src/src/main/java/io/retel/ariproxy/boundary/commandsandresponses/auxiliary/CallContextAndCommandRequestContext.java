package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CallContextAndCommandRequestContext {

	private final String callContext;
	private final String commandId;
	private final AriCommand ariCommand;

	public CallContextAndCommandRequestContext(String callContext, String commandId, AriCommand ariCommand) {
		this.callContext = callContext;
		this.commandId = commandId;
		this.ariCommand = ariCommand;
	}

	public String getCallContext() {
		return callContext;
	}

	public String getCommandId() {
		return commandId;
	}

	public AriCommand getAriCommand() {
		return ariCommand;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
