package io.retel.ariproxy.config;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public final class ServiceConfig {

	private final int httpPort;
	private final String kafkaBootstrapServers;
	private final String kafkaCommandsTopic;
	private final String kafkaConsumerGroup;
	private final String kafkaEventsAndResponsesTopic;
	private final String name;
	private final String restPassword;
	private final String restUri;
	private final String restUser;
	private final String stasisApp;
	private final String websocketUri;
	private final String persistenceStoreClassName;
	private final String redisHost;
	private final int redisPort;
	private final int redisDb;

	private ServiceConfig(final ServiceConfigBuilder builder) {
		this.httpPort = builder.httpPort;
		this.kafkaBootstrapServers = builder.kafkaBootstrapServers;
		this.kafkaCommandsTopic = builder.kafkaCommandsTopic;
		this.kafkaConsumerGroup = builder.kafkaConsumerGroup;
		this.kafkaEventsAndResponsesTopic = builder.kafkaEventsAndResponsesTopic;
		this.name = builder.name;
		this.restPassword = builder.restPassword;
		this.restUri = builder.restUri;
		this.restUser = builder.restUser;
		this.stasisApp = builder.stasisApp;
		this.websocketUri = builder.websocketUri;
		this.persistenceStoreClassName = builder.persistenceStore;
		this.redisHost = builder.redisHost;
		this.redisPort = builder.redisPort;
		this.redisDb = builder.redisDb;
	}

	public int getHttpPort() {
		return this.httpPort;
	}

	public String getKafkaBootstrapServers() {
		return this.kafkaBootstrapServers;
	}

	public String getKafkaCommandsTopic() {
		return this.kafkaCommandsTopic;
	}

	public String getKafkaConsumerGroup() {
		return this.kafkaConsumerGroup;
	}

	public String getKafkaEventsAndResponsesTopic() {
		return this.kafkaEventsAndResponsesTopic;
	}

	public String getName() {
		return this.name;
	}

	public String getRestPassword() {
		return this.restPassword;
	}

	public String getRestUri() {
		return this.restUri;
	}

	public String getRestUser() {
		return this.restUser;
	}

	public String getStasisApp() {
		return this.stasisApp;
	}

	public String getWebsocketUri() {
		return this.websocketUri;
	}

	public String getPersistenceStoreClassName() {
		return this.persistenceStoreClassName;
	}

	public String getRedisHost() {
		return redisHost;
	}

	public int getRedisPort() {
		return redisPort;
	}

	public int getRedisDb() {
		return redisDb;
	}

	public static ServiceConfigBuilder builder() {
		return new ServiceConfigBuilder();
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	static class ServiceConfigBuilder {

		private int httpPort = 8080;
		private String kafkaBootstrapServers;
		private String kafkaCommandsTopic;
		private String kafkaConsumerGroup = "ari-proxy";
		private String kafkaEventsAndResponsesTopic;
		private String name = "ari-proxy";
		private String restPassword = "asterisk";
		private String restUri;
		private String restUser = "asterisk";
		private String stasisApp;
		private String websocketUri;
		private String persistenceStore = "io.retel.ariproxy.persistence.plugin.RedisPersistenceStore";
		private String redisHost;
		private int redisDb;
		private int redisPort = 6379;

		private ServiceConfigBuilder() {
		}

		public ServiceConfig build() {
			return new ServiceConfig(this);
		}

		ServiceConfigBuilder httpPort(final int httpPort) {
			this.httpPort = httpPort;
			return this;
		}

		ServiceConfigBuilder kafkaBootstrapServers(final String kafkaBootstrapServers) {
			this.kafkaBootstrapServers = kafkaBootstrapServers;
			return this;
		}

		ServiceConfigBuilder kafkaCommandsTopic(final String kafkaCommandsTopic) {
			this.kafkaCommandsTopic = kafkaCommandsTopic;
			return this;
		}

		ServiceConfigBuilder kafkaConsumerGroup(final String kafkaConsumerGroup) {
			this.kafkaConsumerGroup = kafkaConsumerGroup;
			return this;
		}

		ServiceConfigBuilder kafkaEventsAndResponsesTopic(final String kafkaEventsAndResponsesTopic) {
			this.kafkaEventsAndResponsesTopic = kafkaEventsAndResponsesTopic;
			return this;
		}

		ServiceConfigBuilder name(final String name) {
			this.name = name;
			return this;
		}

		ServiceConfigBuilder restPassword(final String restPassword) {
			this.restPassword = restPassword;
			return this;
		}

		ServiceConfigBuilder restUri(final String restUri) {
			this.restUri = restUri;
			return this;
		}

		ServiceConfigBuilder restUser(final String restUser) {
			this.restUser = restUser;
			return this;
		}

		ServiceConfigBuilder stasisApp(final String stasisApp) {
			this.stasisApp = stasisApp;
			return this;
		}

		ServiceConfigBuilder websocketUri(final String websocketUri) {
			this.websocketUri = websocketUri;
			return this;
		}

		ServiceConfigBuilder persistenceStore(final String persistenceStore) {
			this.persistenceStore = persistenceStore;
			return this;
		}

		ServiceConfigBuilder redisHost(String host) {
			this.redisHost = host;
			return this;
		}

		ServiceConfigBuilder redisDb(int db) {
			this.redisDb = db;
			return this;
		}

		ServiceConfigBuilder redisPort(int port) {
			this.redisPort = port;
			return this;
		}
	}
}
