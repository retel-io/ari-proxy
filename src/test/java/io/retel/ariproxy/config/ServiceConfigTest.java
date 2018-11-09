package io.retel.ariproxy.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ServiceConfigTest {

    @Test
    void ensureTheServiceConfigIsLoadedProperly() {
        final ServiceConfig config = ConfigLoader.load();

        assertAll(
                "ServiceConfig",
                () -> assertNotNull(config.getHttpPort()),
                () -> assertNotNull(config.getKafkaBootstrapServers()),
                () -> assertNotNull(config.getKafkaCommandsTopic()),
                () -> assertNotNull(config.getKafkaConsumerGroup()),
                () -> assertNotNull(config.getKafkaEventsAndResponsesTopic()),
                () -> assertNotNull(config.getName()),
                () -> assertNotNull(config.getRestPassword()),
                () -> assertNotNull(config.getRestUri()),
                () -> assertNotNull(config.getRestUser()),
                () -> assertNotNull(config.getStasisApp()),
                () -> assertNotNull(config.getWebsocketUri())
        );
    }

    @Test
    void ensureDefaultsAreUsedForOptionalFields() {
        final ServiceConfig config = ConfigLoader.load("no_optionals.conf");

        assertAll(
                "ServiceConfig",
                () -> assertEquals(8080, config.getHttpPort()),
                () -> assertNotNull(config.getKafkaBootstrapServers()),
                () -> assertNotNull(config.getKafkaCommandsTopic()),
                () -> assertNotNull(config.getKafkaConsumerGroup()),
                () -> assertNotNull(config.getKafkaEventsAndResponsesTopic()),
                () -> assertEquals("ari-proxy", config.getName()),
                () -> assertNotNull(config.getRestPassword()),
                () -> assertNotNull(config.getRestUri()),
                () -> assertNotNull(config.getRestUser()),
                () -> assertNotNull(config.getStasisApp()),
                () -> assertNotNull(config.getWebsocketUri())
        );
    }
}