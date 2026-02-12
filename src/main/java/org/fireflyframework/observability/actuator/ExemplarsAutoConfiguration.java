package org.fireflyframework.observability.actuator;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Auto-configuration for Prometheus exemplar linking.
 * <p>
 * When both tracing and Prometheus are active, exemplars are auto-configured by
 * Spring Boot. This configuration ensures the framework's tracing setup is
 * compatible with exemplar generation, enabling users to click a Prometheus
 * metric in Grafana and jump directly to the trace that caused it.
 * <p>
 * Requirements:
 * <ul>
 *     <li>{@code micrometer-registry-prometheus} on classpath</li>
 *     <li>{@code micrometer-tracing-bridge-otel} or {@code micrometer-tracing-bridge-brave} on classpath</li>
 *     <li>Prometheus must be configured with exemplar support (OpenMetrics format)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.prometheusmetrics.PrometheusMeterRegistry")
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.observability.metrics", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ExemplarsAutoConfiguration {
    // Exemplar support is automatically enabled by Spring Boot when both
    // Prometheus and tracing bridges are on the classpath.
    // This configuration class serves as a documentation marker and conditional activation point.
}
