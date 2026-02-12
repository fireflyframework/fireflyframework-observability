# Firefly Framework - Observability

[![CI](https://github.com/fireflyframework/fireflyframework-observability/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-observability/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)

> Centralized observability foundation providing unified metrics, tracing, health indicators, structured logging, and reactive context propagation for the Firefly Framework.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Metrics Exporter Selection](#metrics-exporter-selection)
- [Tracing Bridge Selection](#tracing-bridge-selection)
- [Structured Logging](#structured-logging)
- [Migration Guide](#migration-guide)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Observability is the centralized observability foundation for the entire Firefly ecosystem. It provides consistent abstractions for metrics, distributed tracing, health indicators, and structured logging across all framework modules and downstream microservices.

The library is built on the **Micrometer Observation API** as its instrumentation abstraction, with **OpenTelemetry as the default tracing bridge** (W3C TraceContext propagation with B3 fallback). Both OpenTelemetry and Brave bridges ship on the classpath — switch between them purely via configuration, no POM changes needed.

It solves the critical reactive context propagation problem by enabling `Hooks.enableAutomaticContextPropagation()`, which bridges ThreadLocal values (MDC, trace context) into Reactor Context across all thread boundaries.

All framework modules extend the base classes provided here (`FireflyMetricsSupport`, `FireflyHealthIndicator`, `FireflyTracingSupport`) to ensure consistent metric naming (`firefly.{module}.{metric}`), standardized tag conventions (lowercase.dots), and reactive-safe tracing patterns.

## Features

- **Metrics**: Unified naming via `MetricNaming` (`firefly.{module}.{metric}`), standard tag constants via `MetricTags`, abstract `FireflyMetricsSupport` base class with null-safe, cached, reactive-aware metric creation
- **Tracing**: Reactive-safe `FireflyTracingSupport` wrapping Micrometer Observation API (Mono/Flux), config-based bridge selection (OpenTelemetry default, Brave fallback), W3C + B3 composite propagation
- **Health**: Abstract `FireflyHealthIndicator` base class with error rate, latency, and connection pool thresholds, health-to-metrics bridge for Prometheus gauge exposure, Kubernetes liveness/readiness/startup probe auto-configuration
- **Logging**: Shared `logback-firefly.xml` for structured JSON logging (Logstash encoder), automatic MDC propagation of traceId, spanId, X-Transaction-Id
- **Reactive Context**: Automatic context propagation via `Hooks.enableAutomaticContextPropagation()` — no manual MDC management needed in reactive chains
- **Actuator**: Default endpoint exposure, Prometheus exemplar support (metrics-to-traces linking), OTLP export for traces and metrics
- **WebClient**: Automatic trace context propagation (W3C + B3 + X-Transaction-Id) on all outgoing HTTP requests
- **Auto-configuration**: Spring Boot auto-configuration with `@ConditionalOnMissingBean` backoff — every bean can be overridden

## Requirements

- Java 21+
- Spring Boot 3.5+
- Maven 3.9+

## Installation

Add a single dependency — all tracing bridges, Prometheus registry, and exporters are included:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-observability</artifactId>
</dependency>
```

> Version is managed by `fireflyframework-bom`. No additional dependencies needed for tracing or metrics — everything ships on the classpath.

## Quick Start

### Metrics

```java
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.fireflyframework.observability.metrics.MetricTags;
import io.micrometer.core.instrument.MeterRegistry;

public class OrderMetrics extends FireflyMetricsSupport {

    public OrderMetrics(MeterRegistry registry) {
        super(registry, "orders"); // all metrics prefixed with firefly.orders.*
    }

    public void orderPlaced(String orderType) {
        counter("placed", MetricTags.OPERATION, orderType).increment();
    }

    public <T> Mono<T> timedProcessing(Mono<T> operation) {
        return timed("processing.duration", operation);
    }

    public void orderFailed(Throwable error) {
        recordFailure("placed", error);
    }
}
```

### Health Indicators

```java
import org.fireflyframework.observability.health.FireflyHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class OrderHealthIndicator extends FireflyHealthIndicator {

    public OrderHealthIndicator() {
        super("orders");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        builder.up();
        addErrorRate(builder, getErrorRate(), 0.05);
        addLatency(builder, getP99Latency(), Duration.ofMillis(500));
    }
}
```

### Reactive Tracing

```java
import org.fireflyframework.observability.tracing.FireflyTracingSupport;

@Service
public class OrderService {
    private final FireflyTracingSupport tracingSupport;

    public Mono<Order> processOrder(Order order) {
        return tracingSupport.trace("order.process",
            doProcessOrder(order),
            KeyValues.of("order.type", order.getType()),
            KeyValues.empty()
        );
    }
}
```

## Configuration

All observability settings live under the `firefly.observability` prefix:

```yaml
firefly:
  observability:
    metrics:
      enabled: true                        # Master switch for metrics
      prefix: firefly                      # Default metric prefix
      exporter: PROMETHEUS                 # PROMETHEUS (default), OTLP, or BOTH
    tracing:
      enabled: true                        # Master switch for tracing
      bridge: OTEL                         # OTEL (default) or BRAVE — no POM changes needed
      sampling-probability: 1.0            # Override in production (e.g. 0.1 for 10%)
      propagation-type: W3C               # W3C (default) or B3
      baggage-fields:                      # Custom baggage propagated across services
        - X-Transaction-Id
    health:
      enabled: true                        # Master switch for health indicators
      kubernetes-probes: true              # Configure liveness/readiness/startup groups
    logging:
      enabled: true                        # Master switch for structured logging
      structured-format: logstash          # logstash, ecs, or gelf
    context-propagation:
      reactor-hooks-enabled: true          # Enable Hooks.enableAutomaticContextPropagation()
```

## Metrics Exporter Selection

Both Prometheus and OTLP metrics registries ship on the classpath. An `EnvironmentPostProcessor` reads the `exporter` property at startup and enables/disables the appropriate metrics backends. **No POM changes are needed to switch exporters.**

| Exporter | Endpoint | Protocol | When to Use |
|----------|----------|----------|-------------|
| **PROMETHEUS** (default) | `/actuator/prometheus` | Pull (scrape) | Standard Prometheus/Grafana/Thanos/Mimir setups |
| OTLP | otel-collector-contrib | Push (OTLP) | When all telemetry (traces + metrics) flows through OTel Collector |
| BOTH | Both | Pull + Push | During migration, or when multiple backends consume metrics |

### Prometheus (Default)

```yaml
firefly:
  observability:
    metrics:
      exporter: PROMETHEUS
```

- Exposes `/actuator/prometheus` scrape endpoint
- Standard for Prometheus, Grafana, Thanos, Mimir
- Zero additional infrastructure — Prometheus scrapes the endpoint directly

### OTLP Push

```yaml
firefly:
  observability:
    metrics:
      exporter: OTLP
```

- Pushes metrics to otel-collector-contrib via OTLP protocol
- No scrape endpoint exposed
- Configure the OTLP metrics endpoint via env var:

```bash
OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://otel-collector.monitoring:4318/v1/metrics
```

### Both

```yaml
firefly:
  observability:
    metrics:
      exporter: BOTH
```

- Enables both Prometheus scrape and OTLP push simultaneously
- Useful during migration from Prometheus to OTLP, or when multiple backends consume metrics

## Tracing Bridge Selection

Both OpenTelemetry and Brave tracing bridges ship on the classpath. An `EnvironmentPostProcessor` reads the `bridge` property at startup and excludes the unwanted bridge's Spring Boot auto-configuration. **No POM changes are needed to switch bridges.**

### OpenTelemetry (Default)

```yaml
firefly:
  observability:
    tracing:
      bridge: OTEL
```

- Uses `micrometer-tracing-bridge-otel` with OTLP export
- W3C TraceContext propagation (with B3 fallback via composite propagator)
- Designed for the open-source **[OpenTelemetry Collector (contrib)](https://github.com/open-telemetry/opentelemetry-collector-contrib)** (`otel/opentelemetry-collector-contrib`)
- Compatible with Jaeger, Tempo, Grafana Alloy, Datadog, and any OTLP-compatible backend

### OTLP Transport (gRPC vs HTTP)

Both gRPC and HTTP/protobuf senders are on the classpath. **Default: gRPC (port 4317).** Switch via env var:

| Transport | Port | Env Var | When to Use |
|-----------|------|---------|-------------|
| **gRPC** (default) | 4317 | `OTEL_EXPORTER_OTLP_PROTOCOL=grpc` | Production — persistent connections, binary framing, multiplexing |
| HTTP/protobuf | 4318 | `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf` | Behind HTTP proxies/ALBs, or when gRPC is not available |

```bash
# Default — gRPC to otel-collector-contrib
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.monitoring:4317

# Switch to HTTP/protobuf (e.g., behind an ALB)
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.monitoring:4318

# Per-signal overrides
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://tempo:4317
OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://mimir:4318/v1/metrics
```

### Brave (Zipkin Ecosystems)

```yaml
firefly:
  observability:
    tracing:
      bridge: BRAVE
```

- Uses `micrometer-tracing-bridge-brave` with Zipkin reporter
- B3 propagation (with W3C fallback via composite propagator)
- Compatible with Zipkin-based tracing infrastructure
- Use when migrating from legacy Zipkin/Brave-based systems

## Structured Logging

Include the shared logback configuration in your module's `logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="logback-firefly.xml"/>

    <root level="INFO">
        <appender-ref ref="FIREFLY_ASYNC"/>
    </root>

    <logger name="org.fireflyframework.yourmodule" level="DEBUG"/>
</configuration>
```

This provides:
- JSON structured logging via Logstash encoder (production)
- Pretty-printing with colors and trace context (dev profiles)
- Async appender for production performance
- Automatic MDC fields: `traceId`, `spanId`, `X-Transaction-Id`, `userId`, `correlationId`, `requestId`

## Migration Guide

For framework modules migrating to centralized observability:

1. **Add dependency**: Replace all direct `micrometer-*`, `brave`, `opentelemetry-*`, and `logstash-logback-encoder` dependencies with `fireflyframework-observability`
2. **Metrics**: Extend `FireflyMetricsSupport` — use `counter()`, `timer()`, `gauge()`, `timed()` from the base class instead of `Counter.builder()`, `Timer.builder()` directly
3. **Health**: Extend `FireflyHealthIndicator` — use `addErrorRate()`, `addLatency()` for standard health response formats
4. **Tracing**: Use `FireflyTracingSupport` for reactive-safe span management instead of manual `Observation` lifecycle
5. **Tags**: Use constants from `MetricTags` (lowercase.dots per Micrometer conventions) — e.g. `workflow.id` not `workflowId`
6. **MDC**: Use constants from `MdcConstants` for field names — do NOT use `MDC.put()`/`MDC.remove()` in reactive controllers; use `.contextWrite()` instead
7. **Logback**: Replace custom `logback-spring.xml` with a thin wrapper that does `<include resource="logback-firefly.xml"/>`
8. **DAG**: Add the edge `yourmodule -> observability` in the CLI's `graph.go`

## Architecture

```
fireflyframework-observability/
├── FireflyObservabilityAutoConfiguration          # Master auto-configuration
├── FireflyObservabilityProperties                 # Unified @ConfigurationProperties
├── FireflyObservabilityEnvironmentPostProcessor   # Config-based bridge + exporter selection
├── metrics/
│   ├── FireflyMetricsSupport                      # Abstract base for ALL module metrics
│   ├── MetricNaming                               # Enforces firefly.{module}.{metric}
│   ├── MetricTags                                 # Standard tag constants
│   └── FireflyMeterRegistryCustomizer             # Common tags: app, env, instance
├── tracing/
│   ├── FireflyTracingSupport                      # Reactive-safe tracing (Mono/Flux)
│   ├── FireflyBaggageConfiguration                # Standard baggage fields
│   ├── ReactiveContextPropagationAutoConfiguration # Hooks.enableAutomaticContextPropagation()
│   └── TracingWebClientCustomizer                 # WebClient trace context propagation
├── health/
│   ├── FireflyHealthIndicator                     # Abstract base for health indicators
│   ├── HealthMetricsBridge                        # Health status → Prometheus gauge
│   └── KubernetesProbesAutoConfiguration          # Liveness/readiness/startup probes
├── logging/
│   ├── MdcConstants                               # Standard MDC field names
│   └── StructuredLoggingAutoConfiguration         # Spring Boot structured logging setup
└── actuator/
    ├── FireflyActuatorAutoConfiguration           # Default endpoint exposure
    └── ExemplarsAutoConfiguration                 # Prometheus exemplar linking
```

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
