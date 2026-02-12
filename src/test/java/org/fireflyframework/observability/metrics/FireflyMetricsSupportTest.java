package org.fireflyframework.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FireflyMetricsSupportTest {

    static class TestMetrics extends FireflyMetricsSupport {
        TestMetrics(MeterRegistry registry) {
            super(registry, "test");
        }

        void incrementSuccess() {
            recordSuccess("operations");
        }

        void incrementFailure(Throwable error) {
            recordFailure("operations", error);
        }

        long getCounterValue(String name, String... tags) {
            return (long) counter(name, tags).count();
        }

        <T> Mono<T> timedMono(String name, Mono<T> operation) {
            return timed(name, operation);
        }
    }

    @Test
    void counterIncrementsWithCorrectName() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TestMetrics metrics = new TestMetrics(registry);

        metrics.counter("command.processed").increment();
        metrics.counter("command.processed").increment();

        assertThat(registry.counter("firefly.test.command.processed").count()).isEqualTo(2.0);
    }

    @Test
    void recordSuccessAddStatusTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TestMetrics metrics = new TestMetrics(registry);

        metrics.incrementSuccess();

        assertThat(registry.counter("firefly.test.operations",
                "status", "success").count()).isEqualTo(1.0);
    }

    @Test
    void recordFailureAddsStatusAndErrorTypeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TestMetrics metrics = new TestMetrics(registry);

        metrics.incrementFailure(new IllegalStateException("boom"));

        assertThat(registry.counter("firefly.test.operations",
                "status", "failure",
                "error.type", "IllegalStateException").count()).isEqualTo(1.0);
    }

    @Test
    void timerRecordsDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TestMetrics metrics = new TestMetrics(registry);

        metrics.timer("processing.duration").record(Duration.ofMillis(150));

        assertThat(registry.timer("firefly.test.processing.duration").count()).isEqualTo(1);
        assertThat(registry.timer("firefly.test.processing.duration").totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isGreaterThanOrEqualTo(150.0);
    }

    @Test
    void noopWhenRegistryIsNull() {
        TestMetrics metrics = new TestMetrics(null);

        // Should not throw
        metrics.counter("test").increment();
        metrics.timer("test").record(Duration.ofMillis(100));
        metrics.distributionSummary("test").record(42.0);
        metrics.recordSuccess("test");
        metrics.recordFailure("test", new RuntimeException());

        assertThat(metrics.isEnabled()).isFalse();
    }

    @Test
    void timedMonoRecordsMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TestMetrics metrics = new TestMetrics(registry);

        Mono<String> result = metrics.timedMono("async.operation", Mono.just("hello"));

        StepVerifier.create(result)
                .expectNext("hello")
                .verifyComplete();

        assertThat(registry.timer("firefly.test.async.operation").count()).isEqualTo(1);
    }

    @Test
    void timedMonoNoopWhenRegistryNull() {
        TestMetrics metrics = new TestMetrics(null);

        Mono<String> result = metrics.timedMono("async.operation", Mono.just("hello"));

        StepVerifier.create(result)
                .expectNext("hello")
                .verifyComplete();
    }

    @Test
    void countersCachedAcrossInvocations() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TestMetrics metrics = new TestMetrics(registry);

        var counter1 = metrics.counter("cached.test", "key", "val");
        var counter2 = metrics.counter("cached.test", "key", "val");

        assertThat(counter1).isSameAs(counter2);
    }
}
