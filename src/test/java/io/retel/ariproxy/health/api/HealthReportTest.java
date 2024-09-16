package io.retel.ariproxy.health.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

class HealthReportTest {

  @Test
  void mergeReturnSameInstanceIfOtherIsNull() {
    HealthReport report = HealthReport.ok();
    HealthReport mergedReport = report.merge(null);
    assertThat(mergedReport, is(report));
  }

  @Test
  void mergeReturnOtherInstanceIfIsNotNull() {
    HealthReport report = HealthReport.ok();
    HealthReport mergedReport = report.merge(HealthReport.error("Some Error"));
    assertThat(mergedReport, is(not(report)));
    assertThat(mergedReport.errors(), is(List.of("Some Error")));
  }
}
