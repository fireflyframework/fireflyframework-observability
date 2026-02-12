package org.fireflyframework.observability.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Auto-configuration for structured logging in the Firefly Framework.
 * <p>
 * Spring Boot 3.4+ provides built-in structured logging via
 * {@code logging.structured.format.console}. This auto-configuration ensures
 * the framework's default logging settings are applied via the
 * {@code firefly-observability} Spring profile.
 * <p>
 * The actual logging configuration is split between:
 * <ul>
 *     <li>{@code application-firefly-observability.yml} — Spring Boot structured logging properties</li>
 *     <li>{@code logback-firefly.xml} — advanced Logstash encoder config for projects that need it</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.observability.logging", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class StructuredLoggingAutoConfiguration {
    // Configuration is applied via the default properties in application-firefly-observability.yml
    // and the shared logback-firefly.xml (which projects can <include>).
}
