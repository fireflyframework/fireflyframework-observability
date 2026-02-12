package org.fireflyframework.observability.actuator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;

/**
 * Auto-configuration for Firefly actuator defaults.
 * <p>
 * Ensures standard actuator endpoint exposure and configuration is applied
 * consistently across all Firefly-based applications. The actual endpoint
 * configuration (which endpoints to expose, security, etc.) is driven by
 * {@code application-firefly-observability.yml}.
 */
@AutoConfiguration(after = WebEndpointAutoConfiguration.class)
@ConditionalOnClass(WebEndpointAutoConfiguration.class)
public class FireflyActuatorAutoConfiguration {
    // Configuration is applied via the default properties in application-firefly-observability.yml.
}
