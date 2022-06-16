package emissary.core;

import java.util.Map;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Health Check that returns healthy if JVM heap and garbage collect are at acceptable values.
public class JVMHealthCheck extends HealthCheck {

    protected double maxHeapUsage = 100.00;
    protected double maxNonHeapUsage = 100.00;

    protected static final Logger logger = LoggerFactory.getLogger(MetricsManager.class);

    MetricRegistry metricsManager = MetricsManager.getMetricRegistry();

    public JVMHealthCheck(final double maxHeapUsage, final double maxNonHeapUsage) {
        this.maxHeapUsage = maxHeapUsage;
        this.maxNonHeapUsage = maxNonHeapUsage;
    }

    @Override
    protected Result check() throws Exception {
        Gauge heapUsage = metricsManager.getGauges().get("heap.usage");
        Gauge nonHeapUsage = metricsManager.getGauges().get("non-heap.usage");

        boolean healthy = true;

        // Validate if it is healthy
        if ((Double) heapUsage.getValue() > maxHeapUsage || ((Double) nonHeapUsage.getValue() > maxHeapUsage)) {
            healthy = false;
        }

        Map<String, Metric> memoryMap = metricsManager.getMetrics();
        for (String key : memoryMap.keySet()) {
            Metric m = memoryMap.get(key);
            logger.info(m.getClass().toString());
            if (m instanceof Gauge) {
                logger.info(((Gauge<Long>) m).getValue().toString());
            }
        }


        if (healthy) {
            return Result.healthy("Heap Usage: %s");
        } else {
            return Result.unhealthy("");
        }
    }
}
