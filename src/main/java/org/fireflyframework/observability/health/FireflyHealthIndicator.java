package org.fireflyframework.observability.health;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.time.Duration;

/**
 * Abstract base class for all Firefly health indicators.
 * <p>
 * Provides standard detail helpers for consistent health response formatting
 * across all framework modules. Subclasses implement {@link #doHealthCheck(Health.Builder)}
 * to perform their specific health checks.
 *
 * <pre>{@code
 * public class EdaHealthIndicator extends FireflyHealthIndicator {
 *     public EdaHealthIndicator() {
 *         super("eda");
 *     }
 *
 *     @Override
 *     protected void doHealthCheck(Health.Builder builder) throws Exception {
 *         builder.up()
 *             .withDetail("publishers.active", getActivePublishers());
 *         addErrorRate(builder, getErrorRate(), 0.05);
 *     }
 * }
 * }</pre>
 */
public abstract class FireflyHealthIndicator extends AbstractHealthIndicator {

    private final String componentName;

    protected FireflyHealthIndicator(String componentName) {
        super(componentName + " health check failed");
        this.componentName = componentName;
    }

    /**
     * Returns the component name used in health response details.
     */
    public String getComponentName() {
        return componentName;
    }

    /**
     * Adds a named numeric detail to the health response.
     */
    protected void addMetricDetail(Health.Builder builder, String name, Number value) {
        builder.withDetail(name, value);
    }

    /**
     * Evaluates the error rate against a threshold.
     * If the error rate exceeds the threshold, marks the component as DOWN.
     */
    protected void addErrorRate(Health.Builder builder, double rate, double threshold) {
        builder.withDetail("error.rate", rate);
        builder.withDetail("error.rate.threshold", threshold);
        if (rate > threshold) {
            builder.down();
        }
    }

    /**
     * Evaluates p99 latency against a threshold.
     * If the latency exceeds the threshold, marks the component as DOWN.
     */
    protected void addLatency(Health.Builder builder, Duration p99, Duration threshold) {
        builder.withDetail("latency.p99.ms", p99.toMillis());
        builder.withDetail("latency.threshold.ms", threshold.toMillis());
        if (p99.compareTo(threshold) > 0) {
            builder.down();
        }
    }

    /**
     * Adds connection pool details to the health response.
     */
    protected void addConnectionPool(Health.Builder builder, int active, int idle, int max) {
        builder.withDetail("pool.active", active);
        builder.withDetail("pool.idle", idle);
        builder.withDetail("pool.max", max);
        if (active >= max) {
            builder.down().withDetail("pool.exhausted", true);
        }
    }
}
