package io.retel.ariproxy.health.api;

import akka.actor.ActorRef;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ProvideMonitoring {

	private final String subscriberName;
	private final ActorRef subscriberRef;

	public ProvideMonitoring(String subscriberName, ActorRef subscriberRef) {
		this.subscriberName = subscriberName;
		this.subscriberRef = subscriberRef;
	}

	public String getSubscriberName() {
		return subscriberName;
	}

	public ActorRef getSubscriberRef() {
		return subscriberRef;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
