package io.retel.ariproxy.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.config.ServiceConfig.ServiceConfigBuilder;

public final class ConfigLoader {
	private static final String SERVICE = "service";

	private static final String HTTP_PORT = "httpport";
	private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap-servers";
	private static final String KAFKA_COMMANDS_TOPIC = "kafka.commands-topic";
	private static final String KAFKA_CONSUMER_GROUP = "kafka.consumer-group";
	private static final String KAFKA_EVENTS_AND_RESPONSES_TOPIC = "kafka.events-and-responses-topic";
	private static final String NAME = "name";
	private static final String REST_PASSWORD = "rest.password";
	private static final String REST_URI = "rest.uri";
	private static final String REST_USER = "rest.user";
	private static final String STASIS_APP = "stasis-app";
	private static final String WEBSOCKET_URI = "websocket-uri";

	private ConfigLoader() {}

	public static ServiceConfig load() {
		return parseConfig(ConfigFactory.load().getConfig(SERVICE));
	}

	public static ServiceConfig load(final String resourceBasename) {
		return parseConfig(ConfigFactory.load(resourceBasename).getConfig(SERVICE));
	}

	private static ServiceConfig parseConfig(final Config config) {
		ServiceConfigBuilder builder = ServiceConfig.builder()
				.kafkaBootstrapServers(config.getString(KAFKA_BOOTSTRAP_SERVERS))
				.kafkaCommandsTopic(config.getString(KAFKA_COMMANDS_TOPIC))
				.kafkaConsumerGroup(config.getString(KAFKA_CONSUMER_GROUP))
				.kafkaEventsAndResponsesTopic(config.getString(KAFKA_EVENTS_AND_RESPONSES_TOPIC))
				.restPassword(config.getString(REST_PASSWORD))
				.restUri(config.getString(REST_URI))
				.restUser(config.getString(REST_USER))
				.restPassword(config.getString(REST_USER))
				.stasisApp(config.getString(STASIS_APP))
				.websocketUri(config.getString(WEBSOCKET_URI));

		builder = config.hasPath(NAME) ? builder.name(config.getString(NAME)) : builder;
		builder = config.hasPath(HTTP_PORT) ? builder.httpPort(config.getInt(HTTP_PORT)) : builder;

		return builder.build();
	}
}
