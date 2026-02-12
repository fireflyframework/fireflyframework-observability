package org.fireflyframework.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopDistributionSummary;
import io.micrometer.core.instrument.noop.NoopTimer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Abstract base class for all Firefly module metrics services.
 * <p>
 * Provides null-safe metric creation, consistent naming via {@link MetricNaming},
 * thread-safe caching with {@link ConcurrentHashMap}, and reactive timed operations.
 * <p>
 * When {@code MeterRegistry} is null (e.g., actuator not on classpath), all methods
 * become no-ops — no exceptions, no overhead.
 * <p>
 * Subclasses specify their module name in the constructor, and all metrics are
 * automatically prefixed with {@code firefly.{module}.}.
 *
 * <pre>{@code
 * public class CqrsMetrics extends FireflyMetricsSupport {
 *     public CqrsMetrics(@Nullable MeterRegistry registry) {
 *         super(registry, "cqrs");
 *     }
 *
 *     public void commandProcessed(String commandType) {
 *         counter("command.processed", MetricTags.COMMAND_TYPE, commandType).increment();
 *     }
 * }
 * }</pre>
 */
public abstract class FireflyMetricsSupport {

    private static final Counter NOOP_COUNTER = new NoopCounter(null);
    private static final Timer NOOP_TIMER = new NoopTimer(null);
    private static final DistributionSummary NOOP_SUMMARY = new NoopDistributionSummary(null);

    private final MeterRegistry meterRegistry;
    private final String modulePrefix;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DistributionSummary> summaries = new ConcurrentHashMap<>();

    /**
     * @param meterRegistry the registry, or null if metrics are unavailable
     * @param module        the module identifier (e.g., "cqrs", "eda", "workflow")
     */
    protected FireflyMetricsSupport(MeterRegistry meterRegistry, String module) {
        this.meterRegistry = meterRegistry;
        this.modulePrefix = MetricNaming.prefix(module);
    }

    /**
     * Returns a cached {@link Counter} for the given metric name and tags.
     * Returns a no-op counter when the registry is unavailable.
     */
    protected Counter counter(String name, String... tags) {
        if (meterRegistry == null) return NOOP_COUNTER;
        String fullName = MetricNaming.name(modulePrefix, name);
        return counters.computeIfAbsent(cacheKey(fullName, tags),
                k -> Counter.builder(fullName).tags(tags).register(meterRegistry));
    }

    /**
     * Returns a cached {@link Timer} for the given metric name and tags.
     * Returns a no-op timer when the registry is unavailable.
     */
    protected Timer timer(String name, String... tags) {
        if (meterRegistry == null) return NOOP_TIMER;
        String fullName = MetricNaming.name(modulePrefix, name);
        return timers.computeIfAbsent(cacheKey(fullName, tags),
                k -> Timer.builder(fullName)
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }

    /**
     * Returns a cached {@link DistributionSummary} for the given metric name and tags.
     * Returns a no-op summary when the registry is unavailable.
     */
    protected DistributionSummary distributionSummary(String name, String... tags) {
        if (meterRegistry == null) return NOOP_SUMMARY;
        String fullName = MetricNaming.name(modulePrefix, name);
        return summaries.computeIfAbsent(cacheKey(fullName, tags),
                k -> DistributionSummary.builder(fullName)
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }

    /**
     * Registers a gauge for the given metric name, bound to an object.
     * No-op when the registry is unavailable.
     */
    protected <T> void gauge(String name, T stateObject, ToDoubleFunction<T> valueFunction, String... tags) {
        if (meterRegistry == null) return;
        String fullName = MetricNaming.name(modulePrefix, name);
        io.micrometer.core.instrument.Gauge.builder(fullName, stateObject, valueFunction)
                .tags(tags)
                .register(meterRegistry);
    }

    /**
     * Registers a gauge backed by a {@link java.util.function.Supplier} that returns a numeric value.
     * Useful for lambda-based gauges: {@code gauge("pool.active", () -> activeCount, "service", name)}.
     */
    protected <T extends Number> void gauge(String name, java.util.function.Supplier<T> valueSupplier, String... tags) {
        if (meterRegistry == null) return;
        String fullName = MetricNaming.name(modulePrefix, name);
        io.micrometer.core.instrument.Gauge.builder(fullName, valueSupplier, s -> s.get().doubleValue())
                .tags(tags)
                .register(meterRegistry);
    }

    /**
     * Registers a gauge backed by an {@link AtomicReference} for thread-safe value updates.
     */
    protected void gauge(String name, AtomicReference<? extends Number> ref, String... tags) {
        gauge(name, ref, r -> r.get().doubleValue(), tags);
    }

    /**
     * Wraps a {@link Mono} with timer recording for the given metric name.
     */
    protected <T> Mono<T> timed(String metricName, Mono<T> operation, String... tags) {
        if (meterRegistry == null) return operation;
        Timer t = timer(metricName, tags);
        Timer.Sample sample = Timer.start(meterRegistry);
        return operation.doFinally(signal -> sample.stop(t));
    }

    /**
     * Wraps a {@link Flux} with timer recording for the given metric name.
     */
    protected <T> Flux<T> timed(String metricName, Flux<T> operation, String... tags) {
        if (meterRegistry == null) return operation;
        Timer t = timer(metricName, tags);
        Timer.Sample sample = Timer.start(meterRegistry);
        return operation.doFinally(signal -> sample.stop(t));
    }

    /**
     * Records a successful operation by incrementing a counter with a status=success tag.
     */
    protected void recordSuccess(String metricName, String... extraTags) {
        counter(metricName, merge(MetricTags.STATUS, MetricTags.SUCCESS, extraTags)).increment();
    }

    /**
     * Records a failed operation by incrementing a counter with status=failure and error.type tags.
     */
    protected void recordFailure(String metricName, Throwable error, String... extraTags) {
        counter(metricName, merge(MetricTags.STATUS, MetricTags.FAILURE,
                MetricTags.ERROR_TYPE, error.getClass().getSimpleName(), extraTags)).increment();
    }

    /**
     * Whether this metrics support instance has an active registry.
     */
    public boolean isEnabled() {
        return meterRegistry != null;
    }

    /**
     * Returns the underlying meter registry, or null if unavailable.
     */
    protected MeterRegistry registry() {
        return meterRegistry;
    }

    /**
     * Returns the module prefix (e.g., "firefly.cqrs").
     */
    protected String modulePrefix() {
        return modulePrefix;
    }

    private static String cacheKey(String name, String... tags) {
        if (tags.length == 0) return name;
        StringBuilder sb = new StringBuilder(name);
        for (String tag : tags) {
            sb.append(':').append(tag);
        }
        return sb.toString();
    }

    private static String[] merge(String key1, String val1, String... extraTags) {
        String[] result = new String[2 + extraTags.length];
        result[0] = key1;
        result[1] = val1;
        System.arraycopy(extraTags, 0, result, 2, extraTags.length);
        return result;
    }

    private static String[] merge(String key1, String val1, String key2, String val2, String... extraTags) {
        String[] result = new String[4 + extraTags.length];
        result[0] = key1;
        result[1] = val1;
        result[2] = key2;
        result[3] = val2;
        System.arraycopy(extraTags, 0, result, 4, extraTags.length);
        return result;
    }
}
