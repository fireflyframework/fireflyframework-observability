package org.fireflyframework.observability.health;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

/**
 * Bridges health indicator status to Micrometer gauge metrics.
 * <p>
 * Registers a {@code firefly.health.status} gauge for each registered health indicator,
 * allowing health status to be scraped by Prometheus and displayed in Grafana dashboards.
 * <p>
 * Status mapping: UP=1.0, DOWN=0.0, OUT_OF_SERVICE=-1.0, UNKNOWN=-2.0
 */
public class HealthMetricsBridge {

    private final MeterRegistry meterRegistry;

    public HealthMetricsBridge(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Registers a gauge for a health indicator that reflects its current status as a numeric value.
     */
    public void register(String componentName, HealthIndicator indicator) {
        meterRegistry.gauge("firefly.health.status",
                java.util.List.of(Tag.of("component", componentName)),
                indicator,
                hi -> statusToDouble(hi.health().getStatus()));
    }

    private static double statusToDouble(Status status) {
        if (Status.UP.equals(status)) return 1.0;
        if (Status.DOWN.equals(status)) return 0.0;
        if (Status.OUT_OF_SERVICE.equals(status)) return -1.0;
        return -2.0; // UNKNOWN
    }
}
