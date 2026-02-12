package org.fireflyframework.observability.logging;

/**
 * Standard MDC (Mapped Diagnostic Context) field names used across the Firefly Framework.
 * <p>
 * These constants ensure all modules use the same MDC keys for structured logging,
 * making log aggregation and correlation consistent across services.
 */
public final class MdcConstants {

    private MdcConstants() {
    }

    /** Trace ID from the distributed tracing system. */
    public static final String TRACE_ID = "traceId";

    /** Span ID from the distributed tracing system. */
    public static final String SPAN_ID = "spanId";

    /** Framework-specific transaction identifier (MDC key). */
    public static final String TRANSACTION_ID = "X-Transaction-Id";

    /** HTTP header name for transaction ID propagation. */
    public static final String TRANSACTION_ID_HEADER = "X-Transaction-Id";

    /** User identifier for audit logging. */
    public static final String USER_ID = "userId";

    /** Correlation ID for request correlation across services. */
    public static final String CORRELATION_ID = "correlationId";

    /** Request ID for individual HTTP request tracking. */
    public static final String REQUEST_ID = "requestId";

    /** Service name for multi-service log aggregation. */
    public static final String SERVICE_NAME = "serviceName";

    /** Aggregate type for event sourcing context. */
    public static final String AGGREGATE_TYPE = "aggregateType";

    /** Aggregate ID for event sourcing context. */
    public static final String AGGREGATE_ID = "aggregateId";
}
