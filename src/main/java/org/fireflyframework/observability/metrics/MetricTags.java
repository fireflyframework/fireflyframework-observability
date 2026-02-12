package org.fireflyframework.observability.metrics;

/**
 * Standard tag key and value constants for Firefly Framework metrics.
 * All tag keys use lowercase.dots per Micrometer conventions.
 */
public final class MetricTags {

    private MetricTags() {
    }

    // ===== Standard Keys (lowercase.dots) =====

    public static final String STATUS = "status";
    public static final String ERROR_TYPE = "error.type";
    public static final String OPERATION = "operation";
    public static final String COMMAND_TYPE = "command.type";
    public static final String QUERY_TYPE = "query.type";
    public static final String EVENT_TYPE = "event.type";
    public static final String WORKFLOW_ID = "workflow.id";
    public static final String STEP_ID = "step.id";
    public static final String PROVIDER = "provider";
    public static final String DESTINATION = "destination";
    public static final String PUBLISHER_TYPE = "publisher.type";
    public static final String CONSUMER_TYPE = "consumer.type";
    public static final String TRANSACTION_TYPE = "transaction.type";
    public static final String AGGREGATE_TYPE = "aggregate.type";
    public static final String JOB_NAME = "job.name";
    public static final String JOB_STAGE = "job.stage";
    public static final String CLIENT_TYPE = "client.type";
    public static final String WEBHOOK_TYPE = "webhook.type";
    public static final String NOTIFICATION_TYPE = "notification.type";
    public static final String CHANNEL = "channel";

    // ===== Standard Values =====

    public static final String SUCCESS = "success";
    public static final String FAILURE = "failure";
    public static final String TIMEOUT = "timeout";
    public static final String REJECTED = "rejected";
    public static final String CANCELLED = "cancelled";
}
