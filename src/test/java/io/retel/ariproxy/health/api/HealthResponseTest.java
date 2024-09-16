package io.retel.ariproxy.health.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vavr.collection.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

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
