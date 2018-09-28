package io.retel.ariproxy.boundary.callcontext.api;

import java.io.Serializable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class RegisterCallContext implements Serializable {

	private final String resourceId;
	private final String callContext;

	public RegisterCallContext(String resourceId, String callContext) {
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
