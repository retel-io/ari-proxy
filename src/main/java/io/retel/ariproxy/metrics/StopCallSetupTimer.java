package io.retel.ariproxy.metrics;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class StopCallSetupTimer {

	private String callcontext;
	private String application;

	public StopCallSetupTimer(String callcontext, String application) {
		this.callcontext = callcontext;
		this.application = application;
	}

	public String getCallcontext() {
		return callcontext;
	}

	public String getApplication() {
		return application;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
