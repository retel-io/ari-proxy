package io.retel.ariproxy.metrics;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class RedisUpdateTimerStop {
	private String context;

	public RedisUpdateTimerStop(String context) {
		this.context = context;
	}

	public String getContext() {
		return context;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
