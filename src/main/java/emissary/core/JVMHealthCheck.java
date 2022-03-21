package emissary.core;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;

import java.util.SortedMap;

// Health Check that returns healthy if JVM heap and garbage collect are at acceptable values.
public class JVMHealthCheck extends HealthCheck {

    protected double maxHeapUsage = 100.00;
    protected double maxNonHeapUsage = 100.00;

    MetricRegistry metricsManager = MetricsManager.getMetricRegistry();

    public JVMHealthCheck(final double maxHeapUsage, final double maxNonHeapUsage){
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

        if (healthy) {
            return Result.healthy("Heap Usage: %s");
        } else {
            return Result.unhealthy("");
        }
    }
}
