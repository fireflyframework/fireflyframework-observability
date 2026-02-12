package org.fireflyframework.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.fireflyframework.observability.health.HealthMetricsBridge;
import org.fireflyframework.observability.metrics.FireflyMeterRegistryCustomizer;
import org.fireflyframework.observability.tracing.FireflyBaggageConfiguration;
import org.fireflyframework.observability.tracing.FireflyTracingSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Master auto-configuration for Firefly Framework observability.
 * <p>
 * Activates metrics, tracing, health, and logging support based on classpath
 * detection and configuration properties. All features are enabled by default
 * and can be individually disabled via {@code firefly.observability.*} properties.
 * <p>
 * Each inner configuration class is guarded by its own conditions, ensuring
 * graceful degradation when optional dependencies are absent.
 */
@AutoConfiguration
@EnableConfigurationProperties(FireflyObservabilityProperties.class)
public class FireflyObservabilityAutoConfiguration {

    // --- Metrics ---
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "firefly.observability.metrics", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        FireflyMeterRegistryCustomizer fireflyMeterRegistryCustomizer(
                @Value("${spring.application.name:unknown}") String appName,
                @Value("${spring.profiles.active:default}") String environment) {
            return new FireflyMeterRegistryCustomizer(appName, environment);
        }
    }

    // --- Tracing ---
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObservationRegistry.class)
    @ConditionalOnProperty(prefix = "firefly.observability.tracing", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class TracingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(ObservationRegistry.class)
        FireflyTracingSupport fireflyTracingSupport(ObservationRegistry registry) {
            return new FireflyTracingSupport(registry);
        }

        @Bean
        @ConditionalOnMissingBean
        FireflyBaggageConfiguration fireflyBaggageConfiguration(FireflyObservabilityProperties props) {
            return new FireflyBaggageConfiguration(props.getTracing().getBaggageFields());
        }
    }

    // --- Health ---
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "firefly.observability.health", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class HealthConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean
        HealthMetricsBridge healthMetricsBridge(MeterRegistry meterRegistry) {
            return new HealthMetricsBridge(meterRegistry);
        }
    }
}
