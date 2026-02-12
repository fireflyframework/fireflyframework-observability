package org.fireflyframework.observability.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.fireflyframework.observability.FireflyObservabilityAutoConfiguration;
import org.fireflyframework.observability.FireflyObservabilityProperties;
import org.fireflyframework.observability.health.HealthMetricsBridge;
import org.fireflyframework.observability.metrics.FireflyMeterRegistryCustomizer;
import org.fireflyframework.observability.tracing.FireflyBaggageConfiguration;
import org.fireflyframework.observability.tracing.FireflyTracingSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FireflyObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FireflyObservabilityAutoConfiguration.class));

    @Test
    void metricsCustomizerRegisteredWhenMeterRegistryAvailable() {
        contextRunner
                .withBean(MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyMeterRegistryCustomizer.class);
                });
    }

    @Test
    void tracingSupportRegisteredWhenObservationRegistryAvailable() {
        contextRunner
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyTracingSupport.class);
                    assertThat(context).hasSingleBean(FireflyBaggageConfiguration.class);
                });
    }

    @Test
    void healthMetricsBridgeRegisteredWhenBothAvailable() {
        contextRunner
                .withBean(MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(HealthMetricsBridge.class);
                });
    }

    @Test
    void metricsDisabledViaProperty() {
        contextRunner
                .withPropertyValues("firefly.observability.metrics.enabled=false")
                .withBean(MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FireflyMeterRegistryCustomizer.class);
                });
    }

    @Test
    void tracingDisabledViaProperty() {
        contextRunner
                .withPropertyValues("firefly.observability.tracing.enabled=false")
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FireflyTracingSupport.class);
                });
    }

    @Test
    void propertiesBoundCorrectly() {
        contextRunner
                .withPropertyValues(
                        "firefly.observability.metrics.enabled=true",
                        "firefly.observability.tracing.sampling-probability=0.5",
                        "firefly.observability.tracing.propagation-type=B3",
                        "firefly.observability.health.kubernetes-probes=false",
                        "firefly.observability.logging.structured-format=ecs",
                        "firefly.observability.context-propagation.reactor-hooks-enabled=false"
                )
                .run(context -> {
                    FireflyObservabilityProperties props = context.getBean(FireflyObservabilityProperties.class);
                    assertThat(props.getMetrics().isEnabled()).isTrue();
                    assertThat(props.getTracing().getSamplingProbability()).isEqualTo(0.5);
                    assertThat(props.getTracing().getPropagationType())
                            .isEqualTo(FireflyObservabilityProperties.PropagationType.B3);
                    assertThat(props.getHealth().isKubernetesProbes()).isFalse();
                    assertThat(props.getLogging().getStructuredFormat()).isEqualTo("ecs");
                    assertThat(props.getContextPropagation().isReactorHooksEnabled()).isFalse();
                });
    }

    @Test
    void customBeanBacksOffAutoConfiguration() {
        FireflyTracingSupport customTracing = new FireflyTracingSupport(ObservationRegistry.create());

        contextRunner
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .withBean(FireflyTracingSupport.class, () -> customTracing)
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyTracingSupport.class);
                    assertThat(context.getBean(FireflyTracingSupport.class)).isSameAs(customTracing);
                });
    }
}
