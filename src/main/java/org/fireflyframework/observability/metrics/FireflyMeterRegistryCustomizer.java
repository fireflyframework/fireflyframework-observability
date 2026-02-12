package org.fireflyframework.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;

/**
 * Applies common tags (application, environment, instance) to all metrics
 * registered in the {@link MeterRegistry}. This ensures every metric emitted
 * by any Firefly module carries consistent identifying labels.
 */
public class FireflyMeterRegistryCustomizer implements MeterRegistryCustomizer<MeterRegistry> {

    private final String applicationName;
    private final String environment;

    public FireflyMeterRegistryCustomizer(String applicationName, String environment) {
        this.applicationName = applicationName;
        this.environment = environment;
    }

    @Override
    public void customize(MeterRegistry registry) {
        registry.config()
                .commonTags(
                        "application", applicationName,
                        "environment", environment
                );
    }
}
