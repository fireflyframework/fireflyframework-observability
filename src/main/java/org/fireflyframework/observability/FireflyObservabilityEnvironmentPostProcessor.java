package org.fireflyframework.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures observability backends at startup based on Firefly properties.
 * <p>
 * This post-processor reads two Firefly properties and translates them into
 * Spring Boot auto-configuration exclusions and property overrides:
 *
 * <h3>Tracing Bridge Selection</h3>
 * Both OpenTelemetry and Brave bridges ship on the classpath.
 * {@code firefly.observability.tracing.bridge} controls which one is active:
 * <ul>
 *   <li>{@code OTEL} (default) — excludes {@code BraveAutoConfiguration}</li>
 *   <li>{@code BRAVE} — excludes {@code OpenTelemetryTracingAutoConfiguration}
 *       and {@code OtlpTracingAutoConfiguration}</li>
 * </ul>
 *
 * <h3>Metrics Exporter Selection</h3>
 * Both Prometheus and OTLP metrics registries ship on the classpath.
 * {@code firefly.observability.metrics.exporter} controls which one is active:
 * <ul>
 *   <li>{@code PROMETHEUS} (default) — enables Prometheus scrape, disables OTLP push</li>
 *   <li>{@code OTLP} — disables Prometheus scrape, enables OTLP push</li>
 *   <li>{@code BOTH} — enables both simultaneously</li>
 * </ul>
 * <p>
 * Runs at {@link Ordered#LOWEST_PRECEDENCE} to ensure {@code application.yml}
 * properties are already resolved. Existing user-defined
 * {@code spring.autoconfigure.exclude} entries are preserved and merged.
 *
 * @see FireflyObservabilityProperties.TracingBridge
 * @see FireflyObservabilityProperties.MetricsExporter
 */
public class FireflyObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    // --- Tracing properties ---
    private static final String BRIDGE_PROPERTY = "firefly.observability.tracing.bridge";
    private static final String TRACING_ENABLED_PROPERTY = "firefly.observability.tracing.enabled";

    // --- Metrics properties ---
    private static final String METRICS_ENABLED_PROPERTY = "firefly.observability.metrics.enabled";
    private static final String METRICS_EXPORTER_PROPERTY = "firefly.observability.metrics.exporter";

    // --- Spring Boot properties ---
    private static final String EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";
    private static final String PROMETHEUS_ENABLED = "management.prometheus.metrics.export.enabled";
    private static final String OTLP_METRICS_ENABLED = "management.otlp.metrics.export.enabled";

    private static final String PROPERTY_SOURCE_NAME = "fireflyObservabilityPostProcessor";

    // Spring Boot actuator auto-configuration classes for each tracing bridge.
    // Both current (3.4+) and deprecated (pre-3.4) class names are excluded for maximum safety.
    private static final String BRAVE_AUTO_CONFIG =
            "org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration";
    private static final String OTEL_TRACING_AUTO_CONFIG =
            "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration";
    private static final String OTEL_AUTO_CONFIG_DEPRECATED =
            "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration";
    private static final String OTLP_TRACING_AUTO_CONFIG =
            "org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration";
    private static final String OTLP_AUTO_CONFIG_DEPRECATED =
            "org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new LinkedHashMap<>();

        configureTracingBridge(environment, props);
        configureMetricsExporter(environment, props);

        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        }
    }

    private void configureTracingBridge(ConfigurableEnvironment environment, Map<String, Object> props) {
        boolean tracingEnabled = environment.getProperty(TRACING_ENABLED_PROPERTY, Boolean.class, true);
        if (!tracingEnabled) {
            return;
        }

        String bridge = environment.getProperty(BRIDGE_PROPERTY, "OTEL").toUpperCase().trim();

        String toExclude;
        if ("BRAVE".equals(bridge)) {
            // User selected Brave — exclude OTel bridge and OTLP exporter (both current and deprecated names)
            toExclude = String.join(",",
                    OTEL_TRACING_AUTO_CONFIG, OTEL_AUTO_CONFIG_DEPRECATED,
                    OTLP_TRACING_AUTO_CONFIG, OTLP_AUTO_CONFIG_DEPRECATED);
        } else {
            // Default: OTEL — exclude Brave bridge
            toExclude = BRAVE_AUTO_CONFIG;
        }

        // Merge with any existing exclusions
        String existing = environment.getProperty(EXCLUDE_PROPERTY, "");
        String merged = existing.isBlank() ? toExclude : existing + "," + toExclude;
        props.put(EXCLUDE_PROPERTY, merged);
    }

    private void configureMetricsExporter(ConfigurableEnvironment environment, Map<String, Object> props) {
        boolean metricsEnabled = environment.getProperty(METRICS_ENABLED_PROPERTY, Boolean.class, true);
        if (!metricsEnabled) {
            props.put(PROMETHEUS_ENABLED, "false");
            props.put(OTLP_METRICS_ENABLED, "false");
            return;
        }

        String exporter = environment.getProperty(METRICS_EXPORTER_PROPERTY, "PROMETHEUS").toUpperCase().trim();

        switch (exporter) {
            case "OTLP" -> {
                props.put(PROMETHEUS_ENABLED, "false");
                props.put(OTLP_METRICS_ENABLED, "true");
            }
            case "BOTH" -> {
                props.put(PROMETHEUS_ENABLED, "true");
                props.put(OTLP_METRICS_ENABLED, "true");
            }
            default -> {
                // PROMETHEUS (default)
                props.put(PROMETHEUS_ENABLED, "true");
                props.put(OTLP_METRICS_ENABLED, "false");
            }
        }
    }

    /**
     * Runs after {@code ConfigDataEnvironmentPostProcessor} so that
     * {@code application.yml} properties are available when we read the settings.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
