package io.retel.ariproxy.boundary.callcontext.api;

import java.io.Serializable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ProvideMetrics implements Serializable {

	private ProvideMetrics() {}

	public static ProvideMetrics instance() {
		return new ProvideMetrics();
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
