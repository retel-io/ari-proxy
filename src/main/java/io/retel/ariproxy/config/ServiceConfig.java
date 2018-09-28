package io.retel.ariproxy.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public enum ServiceConfig {
    INSTANCE;

    public static final String SERVICE = "service";
    public static final String SERVICE_NAME = "name";

    public static final String APPLICATION = "application";
    public static final String WEBSOCKET_URI = "websocket-uri";

    public static final String REST_USER = "rest.user";
    public static final String REST_PASSWORD = "rest.password";
    public static final String REST_URI = "rest.uri";

    public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap-servers";
    public static final String KAFKA_CONSUMER_GROUP = "kafka.consumer-group";
    public static final String KAFKA_COMMANDS_TOPIC = "kafka.commands-topic";
    public static final String KAFKA_EVENTS_AND_RESPONSES_TOPIC = "kafka.events-and-responses-topic";

    private static final String CONFIG_PROPERTY_NAME = "ariproxy.configurationFile";

    private Config serviceConfig;

    {
        serviceConfig = ConfigFactory.load().getConfig(SERVICE);
    }

    public Config get() {
        return serviceConfig;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
