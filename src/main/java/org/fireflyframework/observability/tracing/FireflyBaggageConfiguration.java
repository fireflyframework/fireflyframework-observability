package org.fireflyframework.observability.tracing;

import java.util.List;

/**
 * Holds the list of custom baggage field names that should be propagated
 * across service boundaries via trace context headers.
 * <p>
 * By default, the framework propagates {@code X-Transaction-Id} as a baggage field,
 * ensuring it is available in all downstream services and appears in MDC for logging.
 */
public class FireflyBaggageConfiguration {

    private final List<String> baggageFields;

    public FireflyBaggageConfiguration(List<String> baggageFields) {
        this.baggageFields = baggageFields != null ? List.copyOf(baggageFields) : List.of();
    }

    public List<String> getBaggageFields() {
        return baggageFields;
    }
}
