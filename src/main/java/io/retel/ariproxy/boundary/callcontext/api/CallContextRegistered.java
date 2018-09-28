package io.retel.ariproxy.boundary.callcontext.api;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CallContextRegistered {

	private final String resourceId;
	private final String callContext;

	public CallContextRegistered(String resourceId, String callContext) {
		this.resourceId = resourceId;
		this.callContext = callContext;
	}

	public String resourceId() {
		return resourceId;
	}

	public String callContext() {
		return callContext;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
