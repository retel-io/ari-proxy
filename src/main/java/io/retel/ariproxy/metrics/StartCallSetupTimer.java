package io.retel.ariproxy.metrics;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class StartCallSetupTimer {
	private String callContext;

	public StartCallSetupTimer(String callContext) {
		this.callContext = callContext;
	}

	public String getCallContext() {
		return callContext;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
