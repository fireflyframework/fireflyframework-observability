package org.fireflyframework.observability.tracing;

import io.micrometer.context.ContextSnapshot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Hooks;

/**
 * Enables Reactor's automatic context propagation, bridging ThreadLocal values
 * (MDC, OpenTelemetry context, Brave context) into Reactor Context across all
 * operators and thread boundaries.
 * <p>
 * This is THE canonical fix for the reactive/ThreadLocal mismatch that causes:
 * <ul>
 *     <li>Lost trace IDs when reactive chains switch schedulers</li>
 *     <li>MDC values disappearing in downstream operators</li>
 *     <li>Fragmented spans across thread boundaries</li>
 * </ul>
 * <p>
 * Requires {@code io.micrometer:context-propagation} on the classpath (which provides
 * the {@link ContextSnapshot} bridge) and Reactor 3.5.3+ (which provides
 * {@link Hooks#enableAutomaticContextPropagation()}).
 * <p>
 * This eliminates ALL manual MDC management in reactive chains — no more
 * {@code doOnEach()}, {@code doFirst()}, or {@code doFinally()} MDC hacks.
 */
@AutoConfiguration
@ConditionalOnClass({Hooks.class, ContextSnapshot.class})
@ConditionalOnProperty(prefix = "firefly.observability.context-propagation",
        name = "reactor-hooks-enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveContextPropagationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ReactiveContextPropagationAutoConfiguration.class);

    @PostConstruct
    void enableAutomaticContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        log.info("Reactor automatic context propagation enabled — ThreadLocal/MDC values " +
                "will automatically bridge to Reactor Context across thread boundaries");
    }
}
