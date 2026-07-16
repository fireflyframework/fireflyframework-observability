/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.observability.autoconfigure;

import org.fireflyframework.observability.FireflyObservabilityEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class FireflyObservabilityEnvironmentPostProcessorTest {

    private final FireflyObservabilityEnvironmentPostProcessor processor =
            new FireflyObservabilityEnvironmentPostProcessor();

    @Test
    void defaultsAreLoadedFromYaml() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        // The bundled application-firefly-observability.yml should now be on the environment
        // so downstream services get sensible actuator/OTLP defaults out of the box.
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .as("Default actuator endpoint exposure must be applied from framework defaults")
                .contains("prometheus");
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled"))
                .as("Kubernetes liveness/readiness probes must be enabled by default")
                .isEqualTo("true");
        assertThat(environment.getProperty("management.tracing.propagation.type"))
                .as("Trace propagation default should be composite W3C+B3")
                .isEqualTo("W3C,B3");
        assertThat(environment.getProperty("server.shutdown"))
                .as("Graceful shutdown must be on by default")
                .isEqualTo("graceful");
    }

    @Test
    void hostApplicationPropertiesOverrideFrameworkDefaults() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("management.endpoints.web.exposure.include", "health,info");

        processor.postProcessEnvironment(environment, new SpringApplication());

        // The user's existing property must still win — framework defaults are added last.
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
    }

    @Test
    void otelIsDefaultBridge() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        String excluded = environment.getProperty("spring.autoconfigure.exclude", "");
        assertThat(excluded)
                .as("OTel is the default bridge, so the Brave auto-config must be excluded")
                .contains("BraveAutoConfiguration");
        assertThat(excluded).doesNotContain("OpenTelemetryTracingAutoConfiguration");
    }

    @Test
    void switchingToBraveExcludesOtel() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("firefly.observability.tracing.bridge", "BRAVE");

        processor.postProcessEnvironment(environment, new SpringApplication());

        String excluded = environment.getProperty("spring.autoconfigure.exclude", "");
        assertThat(excluded).contains("OpenTelemetryTracingAutoConfiguration");
        assertThat(excluded).contains("OtlpTracingAutoConfiguration");
    }

    @Test
    void metricsOtlpFlippedWhenExporterIsOtlp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("firefly.observability.metrics.exporter", "OTLP");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.prometheus.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("true");
    }

    @Test
    void metricsBothFlipsAllOn() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("firefly.observability.metrics.exporter", "BOTH");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.prometheus.metrics.export.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("true");
    }

    @Test
    void otlpProtocolHttpProtobufTranslatesToHttpTransport() {
        MockEnvironment environment = new MockEnvironment();
        // The OTel-spec value that previously crashed context init by binding straight to the enum.
        environment.setProperty("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.otlp.tracing.transport"))
                .as("http/protobuf must map to the HTTP transport enum, not the raw spec value")
                .isEqualTo("http");
        assertThat(environment.getProperty("management.otlp.tracing.endpoint"))
                .as("HTTP transport must move off the gRPC :4317 port to the :4318 /v1/traces path")
                .isEqualTo("http://localhost:4318/v1/traces");
    }

    @Test
    void otlpProtocolHttpJsonAlsoTranslatesToHttpTransport() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("OTEL_EXPORTER_OTLP_PROTOCOL", "http/json");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.otlp.tracing.transport")).isEqualTo("http");
    }

    @Test
    void otlpProtocolGrpcKeepsGrpcTransportAndPort() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.otlp.tracing.transport")).isEqualTo("grpc");
        assertThat(environment.getProperty("management.otlp.tracing.endpoint"))
                .as("gRPC must keep the default :4317 endpoint")
                .isEqualTo("http://localhost:4317");
    }

    @Test
    void otlpProtocolUnsetLeavesGrpcDefault() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.otlp.tracing.transport"))
                .as("No OTel protocol override means the YAML grpc default stands")
                .isEqualTo("grpc");
    }

    @Test
    void explicitOtlpEndpointIsRespectedWhenSwitchingToHttp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf");
        environment.setProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector.monitoring:4318");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.otlp.tracing.transport")).isEqualTo("http");
        assertThat(environment.getProperty("management.otlp.tracing.endpoint"))
                .as("An explicit endpoint must win over the derived HTTP default")
                .isEqualTo("http://collector.monitoring:4318");
    }

    @Test
    void defaultsLoadingIsIdempotent() {
        MockEnvironment environment = new MockEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        int sizeBefore = sources.size();

        processor.postProcessEnvironment(environment, new SpringApplication());
        int sizeAfterFirst = sources.size();

        processor.postProcessEnvironment(environment, new SpringApplication());
        int sizeAfterSecond = sources.size();

        // First run adds defaults (and possibly the post-processor source). Second run should
        // not duplicate the defaults source.
        assertThat(sizeAfterSecond).isEqualTo(sizeAfterFirst);
        assertThat(sizeAfterFirst).isGreaterThan(sizeBefore);
    }
}
