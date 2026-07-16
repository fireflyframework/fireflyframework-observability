package org.fireflyframework.observability.actuator;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Placeholder for Prometheus exemplar linking. <b>Exemplars are NOT functional in the
 * current pipeline</b> — do not rely on metric→trace click-through yet.
 * <p>
 * This class does not wire anything; it is a documentation/activation marker. For
 * exemplars to actually link a metric sample to a trace, three conditions must ALL hold,
 * and none is met today:
 * <ol>
 *     <li>The {@code firefly.*} timers must publish histogram {@code _bucket} samples —
 *         exemplars anchor to buckets, and buckets are OFF by default (enable via
 *         {@code management.metrics.distribution.percentiles-histogram.firefly=true}).</li>
 *     <li>The exposition must be OpenMetrics 1.0.0 (exemplars are an OpenMetrics feature).</li>
 *     <li>The scrape→collector→store pipeline must preserve OpenMetrics exemplars end to
 *         end; the current scrape-to-GreptimeDB path drops them.</li>
 * </ol>
 * Until the pipeline supports it, correlate metrics to traces via shared resource
 * attributes ({@code application}, {@code k8s_pod_name}) plus timestamp, not exemplars.
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
