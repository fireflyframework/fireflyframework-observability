package org.fireflyframework.observability.tracing;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive-safe tracing support built on the Micrometer Observation API.
 * <p>
 * Wraps {@link Mono} and {@link Flux} operations with named Observation spans,
 * correctly handling all signal types (success, error, cancellation). The Observation
 * is propagated via Reactor Context, NOT ThreadLocal, making it safe for reactive chains
 * that switch schedulers.
 * <p>
 * Usage:
 * <pre>{@code
 * tracingSupport.trace("myService.process", myMono,
 *     KeyValues.of("command.type", "CreateOrder"),
 *     KeyValues.empty());
 * }</pre>
 */
public class FireflyTracingSupport {

    private final ObservationRegistry observationRegistry;

    public FireflyTracingSupport(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /**
     * Wraps a {@link Mono} with a named Observation span.
     * The span is started, propagated via Reactor Context, and correctly
     * stopped on success, error, or cancellation.
     *
     * @param spanName        the span name
     * @param operation       the reactive operation to trace
     * @param lowCardinality  low-cardinality key-values (indexed, used in dashboards)
     * @param highCardinality high-cardinality key-values (not indexed, for trace detail)
     * @return the wrapped Mono
     */
    public <T> Mono<T> trace(String spanName, Mono<T> operation,
                              KeyValues lowCardinality,
                              KeyValues highCardinality) {
        if (observationRegistry == null || observationRegistry.isNoop()) {
            return operation;
        }

        return Mono.deferContextual(ctx -> {
            Observation observation = Observation.createNotStarted(spanName, observationRegistry)
                    .lowCardinalityKeyValues(lowCardinality)
                    .highCardinalityKeyValues(highCardinality)
                    .start();

            return operation
                    .doOnSuccess(v -> observation.stop())
                    .doOnError(e -> {
                        observation.error(e);
                        observation.stop();
                    })
                    .doOnCancel(observation::stop)
                    .contextWrite(c -> c.put(ObservationThreadLocalAccessor.KEY, observation));
        });
    }

    /**
     * Wraps a {@link Mono} with a named Observation span using only the span name.
     */
    public <T> Mono<T> trace(String spanName, Mono<T> operation) {
        return trace(spanName, operation, KeyValues.empty(), KeyValues.empty());
    }

    /**
     * Wraps a {@link Flux} with a named Observation span.
     * The span covers the entire Flux lifecycle (from subscription to terminal signal).
     */
    public <T> Flux<T> trace(String spanName, Flux<T> operation,
                              KeyValues lowCardinality,
                              KeyValues highCardinality) {
        if (observationRegistry == null || observationRegistry.isNoop()) {
            return operation;
        }

        return Flux.deferContextual(ctx -> {
            Observation observation = Observation.createNotStarted(spanName, observationRegistry)
                    .lowCardinalityKeyValues(lowCardinality)
                    .highCardinalityKeyValues(highCardinality)
                    .start();

            return operation
                    .doOnComplete(observation::stop)
                    .doOnError(e -> {
                        observation.error(e);
                        observation.stop();
                    })
                    .doOnCancel(observation::stop)
                    .contextWrite(c -> c.put(ObservationThreadLocalAccessor.KEY, observation));
        });
    }

    /**
     * Wraps a {@link Flux} with a named Observation span using only the span name.
     */
    public <T> Flux<T> trace(String spanName, Flux<T> operation) {
        return trace(spanName, operation, KeyValues.empty(), KeyValues.empty());
    }

    /**
     * Access the current Observation from the Reactor context (NOT ThreadLocal).
     * Returns empty Mono if no observation is in the context.
     */
    public static Mono<Observation> currentObservation() {
        return Mono.deferContextual(ctx ->
                Mono.justOrEmpty(ctx.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
        );
    }

    /**
     * Returns the underlying {@link ObservationRegistry}.
     */
    public ObservationRegistry registry() {
        return observationRegistry;
    }
}
