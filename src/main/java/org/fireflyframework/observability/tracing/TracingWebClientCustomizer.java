package org.fireflyframework.observability.tracing;

import org.fireflyframework.observability.logging.MdcConstants;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Customizes all {@link WebClient} instances to propagate the framework-specific
 * {@code X-Transaction-Id} header on outgoing HTTP requests.
 * <p>
 * Note: Spring Boot's auto-configured {@code ObservationWebClientCustomizer} already
 * handles standard trace context propagation (W3C TraceContext, B3) when
 * {@code micrometer-tracing} is on the classpath. This customizer adds the
 * Firefly-specific transaction ID baggage field on top of that.
 * <p>
 * The transaction ID is read from the Reactor Context first (reactive-safe),
 * falling back to MDC (for non-reactive callers).
 */
@AutoConfiguration
@ConditionalOnClass({WebClient.class, Mono.class})
public class TracingWebClientCustomizer implements WebClientCustomizer {

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter((request, next) ->
                Mono.deferContextual(ctx -> {
                    String txId = ctx.getOrDefault(MdcConstants.TRANSACTION_ID, (String) null);
                    if (txId == null) {
                        txId = MDC.get(MdcConstants.TRANSACTION_ID);
                    }
                    if (txId != null) {
                        return next.exchange(ClientRequest.from(request)
                                .header(MdcConstants.TRANSACTION_ID_HEADER, txId)
                                .build());
                    }
                    return next.exchange(request);
                })
        );
    }
}
