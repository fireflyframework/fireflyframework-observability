package org.fireflyframework.observability.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricNamingTest {

    @Test
    void prefixReturnsFireflyDotModule() {
        assertThat(MetricNaming.prefix("cqrs")).isEqualTo("firefly.cqrs");
        assertThat(MetricNaming.prefix("eda")).isEqualTo("firefly.eda");
        assertThat(MetricNaming.prefix("workflow")).isEqualTo("firefly.workflow");
        assertThat(MetricNaming.prefix("eventsourcing")).isEqualTo("firefly.eventsourcing");
    }

    @Test
    void prefixRejectsInvalidModuleNames() {
        assertThatThrownBy(() -> MetricNaming.prefix(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNaming.prefix(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNaming.prefix("CamelCase"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNaming.prefix("has.dots"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNaming.prefix("has_underscores"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNaming.prefix("1startsWithDigit"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameReturnsFullyQualifiedMetricName() {
        String prefix = MetricNaming.prefix("cqrs");
        assertThat(MetricNaming.name(prefix, "command.processed")).isEqualTo("firefly.cqrs.command.processed");
        assertThat(MetricNaming.name(prefix, "query.duration")).isEqualTo("firefly.cqrs.query.duration");
    }

    @Test
    void nameRejectsBlankMetric() {
        assertThatThrownBy(() -> MetricNaming.name("firefly.cqrs", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNaming.name("firefly.cqrs", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prefixAllowsAlphanumeric() {
        assertThat(MetricNaming.prefix("r2dbc")).isEqualTo("firefly.r2dbc");
        assertThat(MetricNaming.prefix("cache2")).isEqualTo("firefly.cache2");
    }
}
