package emissary.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Timer;
import com.codahale.metrics.ganglia.GangliaReporter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.directory.EmissaryNode;
import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetric.UDPAddressingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the interactions with CodaHale's Metrics package, including configuration
 */
public class MetricsManager {

    public static final String DEFAULT_NAMESPACE_NAME = "MetricsManager";

    @SuppressWarnings("rawtypes")
    public static final SortedMap<String, Gauge> EMPTY_GUAGES = new TreeMap<String, Gauge>();
    public static final SortedMap<String, Counter> EMPTY_COUNTERS = new TreeMap<String, Counter>();
    public static final SortedMap<String, Histogram> EMPTY_HISTOGRAMS = new TreeMap<String, Histogram>();
    public static final SortedMap<String, Meter> EMPTY_METERS = new TreeMap<String, Meter>();

    protected static final Logger logger = LoggerFactory.getLogger(MetricsManager.class);

    protected final MetricRegistry metrics = new MetricRegistry();
    protected final HealthCheckRegistry healthChecks = new HealthCheckRegistry();

    protected final Slf4jReporter reporter;
    protected Configurator conf;

    /**
     * Lookup the default ResourceWatcher in the Namespace
     */
    public static MetricsManager lookup() throws NamespaceException {
        return (MetricsManager) Namespace.lookup(DEFAULT_NAMESPACE_NAME);
    }

    public MetricsManager() {
        configure();
        init();
        this.reporter = Slf4jReporter.forRegistry(this.metrics).build();
    }

    /**
     * Create manager using specified configuration
     * 
     * @param conf the configuration to use
     */
    public MetricsManager(final Configurator conf) {
        this.conf = conf;
        init();
        this.reporter = Slf4jReporter.forRegistry(this.metrics).build();
    }

    public void logMetrics(final Map<String, Timer> stats) {
        final SortedMap<String, Timer> m = new TreeMap<String, Timer>();
        m.putAll(stats);
        this.reporter.report(EMPTY_GUAGES, EMPTY_COUNTERS, EMPTY_HISTOGRAMS, EMPTY_METERS, m);
    }

    public MetricRegistry getMetricRegistry() {
        return this.metrics;
    }

    public HealthCheckRegistry getHealthCheckRegistry() {
        return this.healthChecks;
    }

    protected void configure() {
        try {
            this.conf = ConfigUtil.getConfigInfo(MetricsManager.class);
        } catch (IOException e) {
            logger.warn("Cannot read MetricsManager.cfg, taking default values");
        }
    }

    protected void init() {
        Namespace.bind(DEFAULT_NAMESPACE_NAME, this);

        if (this.conf == null) {
            logger.warn("Configuration is null, skipping init()");
        }

        initMetrics();
        initHealthChecks();
        initJmxReporter();
        initGraphiteReporter();
        initSlf4jReporter();
        initGangliaReporter();
    }

    protected void initHealthChecks() {
        this.healthChecks.register(
                "healthcheck.filequeue",
                new FilePickUpPlaceHealthCheck(this.conf.findIntEntry("MAX_FILE_COUNT_BEFORE_UNHEALTHY", Integer.MAX_VALUE), this.conf.findLongEntry(
                        "MAX_AGGREGATE_FIZE_SIZE_BEFORE_UNHEALTHY_BYTES", Long.MAX_VALUE)));
        this.healthChecks.register("healthcheck.threaddeadlocks", new ThreadDeadlockHealthCheck());
    }

    protected void initMetrics() {
        if (this.conf.findBooleanEntry("JVM_METRICS_ENABLED", false)) {
            logger.debug("JVM Metrics are enabled");
            this.metrics.registerAll(new MemoryUsageGaugeSet());
            this.metrics.registerAll(new GarbageCollectorMetricSet());
            this.metrics.registerAll(new ThreadStatesGaugeSet());
            this.metrics.register("file.descriptor.info", new FileDescriptorRatioGauge());
        } else {
            logger.debug("JVM Metrics are disabled");
        }
    }

    protected void initJmxReporter() {
        if (!this.conf.findBooleanEntry("JMX_METRICS_ENABLED", false)) {
            logger.debug("JMX Metrics are disabled");
            return;
        }

        logger.debug("JMX Metrics are enabled");

        final String domain = this.conf.findStringEntry("JMX_METRICS_DOMAIN", "emissary-metrics");
        final TimeUnit rateUnit = TimeUnit.valueOf(this.conf.findStringEntry("JMX_METRICS_RATE_UNIT", TimeUnit.SECONDS.name()));
        final TimeUnit durationUnit = TimeUnit.valueOf(this.conf.findStringEntry("JMX_METRICS_DURATION_UNIT", TimeUnit.MILLISECONDS.name()));


        final JmxReporter jmxReporter =
                JmxReporter.forRegistry(this.metrics).inDomain(domain).convertRatesTo(rateUnit).convertDurationsTo(durationUnit).build();

        jmxReporter.start();
    }

    protected void initSlf4jReporter() {
        if (!this.conf.findBooleanEntry("SLF4J_METRICS_ENABLED", false)) {
            logger.debug("Slf4j Metrics are disabled");
            return;
        }

        logger.debug("Slf4j Metrics are enabled");

        final String loggerStr = this.conf.findStringEntry("SLF4J_METRICS_LOGGER", "emissary.core.metrics");
        final int interval = this.conf.findIntEntry("SL4J_METRICS_INTERVAL", -1);

        final TimeUnit intervalUnit = TimeUnit.valueOf(this.conf.findStringEntry("SL4J_METRICS_INTERVAL_UNIT", TimeUnit.MINUTES.name()));
        final TimeUnit rateUnit = TimeUnit.valueOf(this.conf.findStringEntry("SLF4J_METRICS_RATE_UNIT", TimeUnit.SECONDS.name()));
        final TimeUnit durationUnit = TimeUnit.valueOf(this.conf.findStringEntry("SLF4J_METRICS_DURATION_UNIT", TimeUnit.MILLISECONDS.name()));

        final Slf4jReporter slf4jReporter =
                Slf4jReporter.forRegistry(this.metrics).outputTo(LoggerFactory.getLogger(loggerStr)).convertRatesTo(rateUnit)
                        .convertDurationsTo(durationUnit).build();

        if (interval > 0) {
            slf4jReporter.start(interval, intervalUnit);
        }
    }

    protected void initGraphiteReporter() {
        if (!this.conf.findBooleanEntry("GRAPHITE_METRICS_ENABLED", false)) {
            logger.debug("Graphite Metrics are disabled");
            return;
        }

        logger.debug("Graphite Metrics are enabled");

        final String prefix = this.conf.findStringEntry("GRAPHITE_METRICS_PREFIX", "emissary");
        final String host = this.conf.findStringEntry("GRAPHITE_METRICS_HOST", "localhost");
        final int port = this.conf.findIntEntry("GRAPHITE_METRICS_PORT", 2003);
        final int interval = this.conf.findIntEntry("GRAPHITE_METRICS_INTERVAL", -1);

        final TimeUnit intervalUnit = TimeUnit.valueOf(this.conf.findStringEntry("GRAPHITE_METRICS_INTERVAL_UNIT", TimeUnit.MINUTES.name()));
        final TimeUnit rateUnit = TimeUnit.valueOf(this.conf.findStringEntry("GRAPHITE_RATE_UNIT", TimeUnit.SECONDS.name()));
        final TimeUnit durationUnit = TimeUnit.valueOf(this.conf.findStringEntry("GRAPHITE_DURATION_UNIT", TimeUnit.MILLISECONDS.name()));

        final Graphite graphite = new Graphite(new InetSocketAddress(host, port));
        final GraphiteReporter graphiteReporter =
                GraphiteReporter.forRegistry(this.metrics).prefixedWith(prefix).convertRatesTo(rateUnit).convertDurationsTo(durationUnit)
                        .filter(MetricFilter.ALL).build(graphite);

        if (interval > 0) {
            graphiteReporter.start(interval, intervalUnit);
        }
    }

    protected void initGangliaReporter() {
        if (!this.conf.findBooleanEntry("GANGLIA_METRICS_ENABLED", false)) {
            logger.debug("Ganglia Metrics are disabled");
            return;
        }

        logger.debug("Ganglia Metrics are enabled");

        final boolean useMulticast = this.conf.findBooleanEntry("GANGLIA_METRICS_MULTICAST", false);

        final String gangliaAddress = this.conf.findStringEntry("GANGLIA_METRICS_ADDRESS", "239.2.11.71");
        final int gangliaPort = this.conf.findIntEntry("GANGLIA_METRICS_PORT", 8649);

        final int interval = this.conf.findIntEntry("GANGLIA_METRICS_INTERVAL", -1);

        final TimeUnit intervalUnit = TimeUnit.valueOf(this.conf.findStringEntry("GANGLIA_METRICS_INTERVAL_UNIT", TimeUnit.MINUTES.name()));
        final TimeUnit rateUnit = TimeUnit.valueOf(this.conf.findStringEntry("GANGLIA_RATE_UNIT", TimeUnit.SECONDS.name()));
        final TimeUnit durationUnit = TimeUnit.valueOf(this.conf.findStringEntry("GANGLIA_DURATION_UNIT", TimeUnit.MILLISECONDS.name()));

        logger.debug("Sending ganglia stats every " + interval + " " + intervalUnit);

        try {
            final GMetric ganglia;
            if (useMulticast) {
                ganglia = new GMetric(gangliaAddress, gangliaPort, UDPAddressingMode.MULTICAST, 1);
            } else {
                ganglia = new GMetric(gangliaAddress, gangliaPort, UDPAddressingMode.UNICAST, 1);
            }
            final GangliaReporter gangliaReporter =
                    GangliaReporter.forRegistry(this.metrics).prefixedWith(System.getProperty(EmissaryNode.NODE_PORT_PROPERTY))
                            .convertRatesTo(rateUnit).convertDurationsTo(durationUnit).build(ganglia);
            logger.debug("using prefix " + System.getProperty(EmissaryNode.NODE_PORT_PROPERTY));
            gangliaReporter.start(interval, intervalUnit);

        } catch (IOException e) {
            logger.error("Error creating GangliaReporter " + e);
            return;
        }
    }
}
