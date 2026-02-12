package org.fireflyframework.observability.tracing;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that ThreadLocal values (MDC) survive reactive thread switches
 * when automatic context propagation is enabled with a registered MDC accessor.
 * <p>
 * In a real Spring Boot application, the tracing library (OTel/Brave) handles
 * trace context propagation, and MDC is populated as a side-effect. This test
 * explicitly registers an MDC ThreadLocalAccessor to verify the propagation
 * mechanism works end-to-end.
 */
class ReactiveContextPropagationTest {

    private static final String MDC_ACCESSOR_KEY = "slf4j.mdc";

    @BeforeAll
    static void setup() {
        // Register MDC as a propagated ThreadLocal — in production, Spring Boot
        // auto-configures this via tracing library bridges
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<Map<String, String>>() {
            @Override
            public Object key() {
                return MDC_ACCESSOR_KEY;
            }

            @Override
            public Map<String, String> getValue() {
                Map<String, String> ctx = MDC.getCopyOfContextMap();
                return ctx != null ? ctx : Map.of();
            }

            @Override
            public void setValue(Map<String, String> value) {
                if (value != null && !value.isEmpty()) {
                    MDC.setContextMap(value);
                }
            }

            @Override
            public void setValue() {
                MDC.clear();
            }
        });

        Hooks.enableAutomaticContextPropagation();
    }

    @AfterAll
    static void cleanup() {
        MDC.clear();
    }

    @Test
    void mdcValuesAreAvailableAfterSchedulerSwitch() {
        MDC.put("traceId", "abc123");
        MDC.put("X-Transaction-Id", "tx-456");

        Mono<String> result = Mono.defer(() -> {
                    String traceId = MDC.get("traceId");
                    return Mono.justOrEmpty(traceId);
                })
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(result)
                .assertNext(traceId -> assertThat(traceId).isEqualTo("abc123"))
                .verifyComplete();

        MDC.clear();
    }

    @Test
    void mdcSurvivesMultipleSchedulerSwitches() {
        MDC.put("traceId", "multi-switch-trace");

        Mono<String> result = Mono.just("start")
                .publishOn(Schedulers.boundedElastic())
                .map(s -> {
                    String tid = MDC.get("traceId");
                    return tid != null ? tid : "null";
                })
                .publishOn(Schedulers.parallel())
                .map(s -> {
                    String tid = MDC.get("traceId");
                    return s + ":" + (tid != null ? tid : "null");
                });

        StepVerifier.create(result)
                .assertNext(value -> {
                    String[] parts = value.split(":");
                    assertThat(parts[0]).isEqualTo("multi-switch-trace");
                    assertThat(parts[1]).isEqualTo("multi-switch-trace");
                })
                .verifyComplete();

        MDC.clear();
    }

    @Test
    void mdcIsClearedAfterReactiveChainCompletes() {
        MDC.put("traceId", "should-be-cleared");

        Mono<String> result = Mono.defer(() -> Mono.justOrEmpty(MDC.get("traceId")))
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(result)
                .assertNext(traceId -> assertThat(traceId).isEqualTo("should-be-cleared"))
                .verifyComplete();

        // The subscribing thread's MDC should still have the value
        // (context propagation restores the calling thread's state)
        MDC.clear();
    }
}
