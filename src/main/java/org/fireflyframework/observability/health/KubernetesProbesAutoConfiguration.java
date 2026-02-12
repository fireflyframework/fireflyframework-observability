package org.fireflyframework.observability.health;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.actuate.health.HealthEndpoint;

/**
 * Auto-configuration for Kubernetes health probe groups.
 * <p>
 * When enabled, configures the standard health endpoint groups:
 * <ul>
 *     <li>{@code /actuator/health/liveness} — only livenessState (NEVER check DB in liveness)</li>
 *     <li>{@code /actuator/health/readiness} — readinessState + db + redis + diskSpace</li>
 * </ul>
 * <p>
 * The actual group configuration is done via {@code application-firefly-observability.yml}
 * which is included as a default Spring profile. This class serves as the conditional
 * activation point.
 */
@AutoConfiguration
@ConditionalOnClass(HealthEndpoint.class)
@ConditionalOnProperty(prefix = "firefly.observability.health", name = "kubernetes-probes",
        havingValue = "true", matchIfMissing = true)
public class KubernetesProbesAutoConfiguration {
    // Configuration is applied via the default properties in application-firefly-observability.yml.
    // This class ensures the auto-configuration is registered and conditional.
}
