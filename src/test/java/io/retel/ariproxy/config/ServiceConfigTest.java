package io.retel.ariproxy.config;

import static io.retel.ariproxy.config.ServiceConfig.APPLICATION;
import static io.retel.ariproxy.config.ServiceConfig.KAFKA_BOOTSTRAP_SERVERS;
import static io.retel.ariproxy.config.ServiceConfig.KAFKA_COMMANDS_TOPIC;
import static io.retel.ariproxy.config.ServiceConfig.KAFKA_CONSUMER_GROUP;
import static io.retel.ariproxy.config.ServiceConfig.KAFKA_EVENTS_AND_RESPONSES_TOPIC;
import static io.retel.ariproxy.config.ServiceConfig.REST_PASSWORD;
import static io.retel.ariproxy.config.ServiceConfig.REST_URI;
import static io.retel.ariproxy.config.ServiceConfig.REST_USER;
import static io.retel.ariproxy.config.ServiceConfig.SERVICE_NAME;
import static io.retel.ariproxy.config.ServiceConfig.WEBSOCKET_URI;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceConfigTest {

    @Test
    @DisplayName("The application.conf file should contain the sections 'rest' and 'kafka' with all their properties")
    void ensureConfigfileContainsDefinesAllRequiredProperties() {
        final Config config = ServiceConfig.INSTANCE.get();

        assertAll(
                "ServiceConfig",
                () -> assertTrue(config.hasPath(SERVICE_NAME), SERVICE_NAME),
                () -> assertTrue(config.hasPath(WEBSOCKET_URI), WEBSOCKET_URI),
                () -> assertTrue(config.hasPath(APPLICATION), APPLICATION),
                () -> assertTrue(config.hasPath(REST_USER), REST_USER),
                () -> assertTrue(config.hasPath(REST_PASSWORD), REST_PASSWORD),
                () -> assertTrue(config.hasPath(REST_URI), REST_URI),
                () -> assertTrue(config.hasPath(KAFKA_BOOTSTRAP_SERVERS), KAFKA_BOOTSTRAP_SERVERS),
                () -> assertTrue(config.hasPath(KAFKA_CONSUMER_GROUP), KAFKA_CONSUMER_GROUP),
                () -> assertTrue(config.hasPath(KAFKA_COMMANDS_TOPIC), KAFKA_COMMANDS_TOPIC),
                () -> assertTrue(config.hasPath(KAFKA_EVENTS_AND_RESPONSES_TOPIC), KAFKA_EVENTS_AND_RESPONSES_TOPIC)
        );
    }

}