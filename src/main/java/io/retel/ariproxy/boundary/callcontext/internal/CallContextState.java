package io.retel.ariproxy.boundary.callcontext.internal;

import akka.Done;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CallContextState implements Serializable {

	private final int maxSize;
	final Map<String, String> callContextByResource;

	public CallContextState(int maxSize, long expirationMillis) {
		this.maxSize = maxSize;
		this.callContextByResource = ExpiringMap.builder()
				.expirationPolicy(ExpirationPolicy.CREATED)
				.expiration(expirationMillis, TimeUnit.MILLISECONDS)
				.maxSize(maxSize)
				.build();
	}

	public String update(String resourceId, String callContext) {
		callContextByResource.put(resourceId, callContext);
		return callContext;
	}

	public Option<String> get(String key) {
		return Option.of(callContextByResource.get(key));
	}

	public int maxSize() {
		return maxSize;
	}

	public int currentSize() {
		return callContextByResource.size();
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
