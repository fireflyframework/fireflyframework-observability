package org.fireflyframework.observability.tracing;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FireflyTracingSupportTest {

    @Test
    void traceMonoCreatesAndStopsObservation() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        FireflyTracingSupport support = new FireflyTracingSupport(registry);

        Mono<String> traced = support.trace("test.operation", Mono.just("result"));

        StepVerifier.create(traced)
                .expectNext("result")
                .verifyComplete();

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("test.operation")
                .that()
                .hasBeenStopped();
    }

    @Test
    void traceMonoRecordsErrorOnFailure() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        FireflyTracingSupport support = new FireflyTracingSupport(registry);

        Mono<String> traced = support.trace("test.failing",
                Mono.error(new RuntimeException("boom")));

        StepVerifier.create(traced)
                .expectError(RuntimeException.class)
                .verify();

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("test.failing")
                .that()
                .hasBeenStopped();
    }

    @Test
    void traceFluxCreatesAndStopsObservation() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        FireflyTracingSupport support = new FireflyTracingSupport(registry);

        Flux<Integer> traced = support.trace("test.flux", Flux.just(1, 2, 3));

        StepVerifier.create(traced)
                .expectNext(1, 2, 3)
                .verifyComplete();

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("test.flux")
                .that()
                .hasBeenStopped();
    }

    @Test
    void traceWithLowCardinalityKeyValues() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        FireflyTracingSupport support = new FireflyTracingSupport(registry);

        Mono<String> traced = support.trace("test.tagged", Mono.just("ok"),
                KeyValues.of("command.type", "CreateOrder"),
                KeyValues.empty());

        StepVerifier.create(traced)
                .expectNext("ok")
                .verifyComplete();

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("test.tagged")
                .that()
                .hasLowCardinalityKeyValue("command.type", "CreateOrder")
                .hasBeenStopped();
    }

    @Test
    void noopRegistryPassesThroughWithoutTracing() {
        FireflyTracingSupport support = new FireflyTracingSupport(ObservationRegistry.NOOP);

        Mono<String> traced = support.trace("test.noop", Mono.just("pass-through"));

        StepVerifier.create(traced)
                .expectNext("pass-through")
                .verifyComplete();
    }

    @Test
    void currentObservationReturnsEmptyWhenNoContext() {
        StepVerifier.create(FireflyTracingSupport.currentObservation())
                .verifyComplete();
    }
}
