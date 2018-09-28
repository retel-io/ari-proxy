package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CallContextAndResourceId {
	private final String callContext;
	private final String resourceId;

	public CallContextAndResourceId(String callContext, String resourceId) {
		this.callContext = callContext;
		this.resourceId = resourceId;
	}

	public String getCallContext() {
		return callContext;
	}

	public String getResourceId() {
		return resourceId;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
