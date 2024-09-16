package io.retel.ariproxy.health.api;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HealthResponseTest {

    @Test
    void ensureEmptyListGeneratesIsOk() {
        HealthResponse response = HealthResponse.fromErrors(new ArrayList<>());
        assertThat(response.isOk(), is(true));
        assertThat(response.errors(), is(new ArrayList()));
    }

    @Test
    void ensureFullListGeneratesIsNotOk() {
        HealthResponse response = HealthResponse.fromErrors(List.of("Error Message").asJava());
        assertThat(response.isOk(), is(false));
        assertThat(response.errors(), is(List.of("Error Message").asJava()));
    }
}