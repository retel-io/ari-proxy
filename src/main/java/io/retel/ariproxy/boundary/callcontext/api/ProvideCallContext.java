package io.retel.ariproxy.boundary.callcontext.api;

import java.io.Serializable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ProvideCallContext implements Serializable {

	private final String resourceId;
	private final ProviderPolicy policy;

	public ProvideCallContext(String resourceId, ProviderPolicy policy) {
		this.resourceId = resourceId;
		this.policy = policy;
	}

	public String resourceId() {
		return resourceId;
	}

	public ProviderPolicy policy() {
		return policy;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
