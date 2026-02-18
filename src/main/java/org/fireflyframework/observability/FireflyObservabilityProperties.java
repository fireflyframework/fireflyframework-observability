package org.fireflyframework.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified configuration properties for Firefly observability.
 * <p>
 * All observability settings are under the {@code firefly.observability} prefix:
 * <pre>{@code
 * firefly:
 *   observability:
 *     metrics:
 *       enabled: true
 *       exporter: PROMETHEUS    # PROMETHEUS (default), OTLP, or BOTH
 *     tracing:
 *       enabled: true
 *       sampling-probability: 1.0
 *       propagation-type: W3C
 *       baggage-fields:
 *         - X-Transaction-Id
 *     health:
 *       enabled: true
 *       kubernetes-probes: true
 *     logging:
 *       enabled: true
 *       structured-format: logstash
 *     context-propagation:
 *       reactor-hooks-enabled: true
 * }</pre>
 */
@Validated
@ConfigurationProperties(prefix = "firefly.observability")
public class FireflyObservabilityProperties {

    private final Metrics metrics = new Metrics();
    private final Tracing tracing = new Tracing();
    private final Health health = new Health();
    private final Logging logging = new Logging();
    private final ContextPropagation contextPropagation = new ContextPropagation();

    public Metrics getMetrics() {
        return metrics;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public Health getHealth() {
        return health;
    }

    public Logging getLogging() {
        return logging;
    }

    public ContextPropagation getContextPropagation() {
        return contextPropagation;
    }

    public static class Metrics {
        private boolean enabled = true;
        private String prefix = "firefly";
        private MetricsExporter exporter = MetricsExporter.PROMETHEUS;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public MetricsExporter getExporter() {
            return exporter;
        }

        public void setExporter(MetricsExporter exporter) {
            this.exporter = exporter;
        }
    }

    public static class Tracing {
        private boolean enabled = true;
        private TracingBridge bridge = TracingBridge.OTEL;
        private double samplingProbability = 1.0;
        private PropagationType propagationType = PropagationType.W3C;
        private List<String> baggageFields = new ArrayList<>(List.of("X-Transaction-Id"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public TracingBridge getBridge() {
            return bridge;
        }

        public void setBridge(TracingBridge bridge) {
            this.bridge = bridge;
        }

        public double getSamplingProbability() {
            return samplingProbability;
        }

        public void setSamplingProbability(double samplingProbability) {
            this.samplingProbability = samplingProbability;
        }

        public PropagationType getPropagationType() {
            return propagationType;
        }

        public void setPropagationType(PropagationType propagationType) {
            this.propagationType = propagationType;
        }

        public List<String> getBaggageFields() {
            return baggageFields;
        }

        public void setBaggageFields(List<String> baggageFields) {
            this.baggageFields = baggageFields;
        }
    }

    public static class Health {
        private boolean enabled = true;
        private boolean kubernetesProbes = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isKubernetesProbes() {
            return kubernetesProbes;
        }

        public void setKubernetesProbes(boolean kubernetesProbes) {
            this.kubernetesProbes = kubernetesProbes;
        }
    }

    public static class Logging {
        private boolean enabled = true;
        private String structuredFormat = "logstash";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStructuredFormat() {
            return structuredFormat;
        }

        public void setStructuredFormat(String structuredFormat) {
            this.structuredFormat = structuredFormat;
        }
    }

    public static class ContextPropagation {
        private boolean reactorHooksEnabled = true;

        public boolean isReactorHooksEnabled() {
            return reactorHooksEnabled;
        }

        public void setReactorHooksEnabled(boolean reactorHooksEnabled) {
            this.reactorHooksEnabled = reactorHooksEnabled;
        }
    }

    public enum PropagationType {
        W3C, B3
    }

    /**
     * Tracing bridge implementation to use.
     * <p>
     * Both OpenTelemetry and Brave bridges are included on the classpath.
     * This property controls which one is active. No pom.xml changes needed.
     * <ul>
     *   <li>{@code OTEL} (default) - OpenTelemetry with OTLP export, W3C TraceContext propagation</li>
     *   <li>{@code BRAVE} - Brave/Zipkin with B3 propagation (backward compat with Zipkin ecosystems)</li>
     * </ul>
     */
    public enum TracingBridge {
        OTEL, BRAVE
    }

    /**
     * Metrics exporter backend to use.
     * <p>
     * Both Prometheus and OTLP metrics registries are included on the classpath.
     * This property controls which one is active. No pom.xml changes needed.
     * <ul>
     *   <li>{@code PROMETHEUS} (default) — pull-based scrape endpoint at {@code /actuator/prometheus}.
     *       Standard for Prometheus/Grafana/Thanos/Mimir setups.</li>
     *   <li>{@code OTLP} — push-based export to an OTLP receiver (e.g., otel-collector-contrib).
     *       Uses the same endpoint configured for trace export.</li>
     *   <li>{@code BOTH} — enables both Prometheus scrape and OTLP push simultaneously.
     *       Useful during migration or when multiple backends consume metrics.</li>
     * </ul>
     */
    public enum MetricsExporter {
        PROMETHEUS, OTLP, BOTH
    }
}
