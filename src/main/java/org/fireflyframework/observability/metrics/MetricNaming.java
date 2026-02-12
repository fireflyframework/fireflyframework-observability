package org.fireflyframework.observability.metrics;

/**
 * Enforces consistent metric naming conventions across the Firefly Framework.
 * All metrics follow the pattern: {@code firefly.{module}.{metric}}.
 */
public final class MetricNaming {

    public static final String FIREFLY_PREFIX = "firefly";

    private MetricNaming() {
    }

    /**
     * Returns "firefly.{module}" — validates module is lowercase alphanumeric.
     *
     * @param module the module name (e.g., "cqrs", "eda", "workflow")
     * @return the full prefix (e.g., "firefly.cqrs")
     * @throws IllegalArgumentException if module doesn't match naming rules
     */
    public static String prefix(String module) {
        if (module == null || !module.matches("[a-z][a-z0-9]*")) {
            throw new IllegalArgumentException(
                    "Module must be lowercase alphanumeric starting with a letter: " + module);
        }
        return FIREFLY_PREFIX + "." + module;
    }

    /**
     * Returns "firefly.{module}.{metric}" — the fully qualified metric name.
     *
     * @param prefix the module prefix from {@link #prefix(String)}
     * @param metric the metric name (e.g., "command.processed", "publish.duration")
     * @return the full metric name
     */
    public static String name(String prefix, String metric) {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("Metric name must not be blank");
        }
        return prefix + "." + metric;
    }
}
