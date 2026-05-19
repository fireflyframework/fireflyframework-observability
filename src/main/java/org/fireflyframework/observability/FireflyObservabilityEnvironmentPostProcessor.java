package org.fireflyframework.observability;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

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
    private static final String DEFAULTS_SOURCE_NAME = "fireflyObservabilityDefaults";
    private static final String DEFAULTS_YAML = "application-firefly-observability.yml";

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
        // 1. Load framework default observability properties (actuator endpoints,
        //    health groups, OTLP endpoints, etc.) — added with LOWEST precedence so the
        //    user's own application.yml always wins.
        loadFrameworkDefaults(environment);

        // 2. Resolve the active tracing bridge & metrics exporter and install the
        //    corresponding Spring Boot auto-config exclusions / enable flags with
        //    HIGHEST precedence.
        Map<String, Object> props = new LinkedHashMap<>();
        configureTracingBridge(environment, props);
        configureMetricsExporter(environment, props);
        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        }
    }

    /**
     * Loads {@code application-firefly-observability.yml} from the classpath and adds
     * its contents to the environment with the lowest precedence so that any property
     * set by the host application overrides them.
     * <p>
     * If the resource is missing or unreadable, the method silently no-ops — the
     * framework still works, the host just won't get the default endpoint exposure,
     * tracing settings, OTLP endpoints, etc., until they opt in explicitly.
     */
    private void loadFrameworkDefaults(ConfigurableEnvironment environment) {
        if (environment.getPropertySources().contains(DEFAULTS_SOURCE_NAME)) {
            return; // idempotent — never double-load if invoked twice
        }
        Resource resource = new ClassPathResource(DEFAULTS_YAML);
        if (!resource.exists()) {
            return;
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);
        Properties properties = yaml.getObject();
        if (properties == null || properties.isEmpty()) {
            return;
        }
        environment.getPropertySources().addLast(
                new PropertiesPropertySource(DEFAULTS_SOURCE_NAME, properties));
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
