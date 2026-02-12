package org.fireflyframework.observability.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FireflyHealthIndicatorTest {

    static class TestHealthIndicator extends FireflyHealthIndicator {
        private boolean healthy = true;
        private double errorRate = 0.0;
        private Duration latency = Duration.ofMillis(50);

        TestHealthIndicator() {
            super("test");
        }

        void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        void setErrorRate(double errorRate) {
            this.errorRate = errorRate;
        }

        void setLatency(Duration latency) {
            this.latency = latency;
        }

        @Override
        protected void doHealthCheck(Health.Builder builder) {
            if (healthy) {
                builder.up();
            } else {
                builder.down();
            }
            addMetricDetail(builder, "active.connections", 42);
            addErrorRate(builder, errorRate, 0.05);
            addLatency(builder, latency, Duration.ofMillis(500));
        }
    }

    @Test
    void healthyIndicatorReportsUp() {
        TestHealthIndicator indicator = new TestHealthIndicator();
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("active.connections", 42);
        assertThat(health.getDetails()).containsEntry("error.rate", 0.0);
        assertThat(health.getDetails()).containsEntry("error.rate.threshold", 0.05);
    }

    @Test
    void highErrorRateMarksDown() {
        TestHealthIndicator indicator = new TestHealthIndicator();
        indicator.setErrorRate(0.10); // 10% > 5% threshold
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void highLatencyMarksDown() {
        TestHealthIndicator indicator = new TestHealthIndicator();
        indicator.setLatency(Duration.ofMillis(1000)); // 1000ms > 500ms threshold
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("latency.p99.ms", 1000L);
        assertThat(health.getDetails()).containsEntry("latency.threshold.ms", 500L);
    }

    @Test
    void componentNameIsSet() {
        TestHealthIndicator indicator = new TestHealthIndicator();
        assertThat(indicator.getComponentName()).isEqualTo("test");
    }

    @Test
    void connectionPoolExhaustedMarksDown() {
        FireflyHealthIndicator indicator = new FireflyHealthIndicator("pool-test") {
            @Override
            protected void doHealthCheck(Health.Builder builder) {
                builder.up();
                addConnectionPool(builder, 10, 0, 10); // active == max
            }
        };

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("pool.exhausted", true);
    }
}
