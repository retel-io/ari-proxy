package io.retel.ariproxy.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.config.ServiceConfig.ServiceConfigBuilder;
import java.util.function.BiFunction;

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
				.kafkaEventsAndResponsesTopic(config.getString(KAFKA_EVENTS_AND_RESPONSES_TOPIC))
				.restUri(config.getString(REST_URI))
				.stasisApp(config.getString(STASIS_APP))
				.websocketUri(config.getString(WEBSOCKET_URI));

		builder = setOptionalValue(config, builder, ServiceConfigBuilder::httpPort, HTTP_PORT, Config::getInt);
		builder = setOptionalValue(config, builder, ServiceConfigBuilder::kafkaConsumerGroup, KAFKA_CONSUMER_GROUP);
		builder = setOptionalValue(config, builder, ServiceConfigBuilder::name, NAME);
		builder = setOptionalValue(config, builder, ServiceConfigBuilder::restPassword, REST_PASSWORD);
		builder = setOptionalValue(config, builder, ServiceConfigBuilder::restUser, REST_USER);

		return builder.build();
	}

	private static ServiceConfigBuilder setOptionalValue(final Config config, final ServiceConfigBuilder builder,
			final BiFunction<ServiceConfigBuilder, String, ServiceConfigBuilder> setter,
			final String key) {
		return setOptionalValue(config, builder, setter, key, Config::getString);
	}

	private static <T> ServiceConfigBuilder setOptionalValue(final Config config, final ServiceConfigBuilder builder,
			final BiFunction<ServiceConfigBuilder, T, ServiceConfigBuilder> setter,
			final String key,
			final BiFunction<Config, String, T> getVal) {
		return config.hasPath(key) ? setter.apply(builder, getVal.apply(config, key)) : builder;
	}
}
