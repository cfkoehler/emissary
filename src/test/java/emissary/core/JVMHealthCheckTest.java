package emissary.core;

import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class JVMHealthCheckTest extends UnitTest {

    @Before
    public void configureTestLoggers() {

    }


    @Test
    public void testJVMHealthCheck() {
        // Create Metrics
        MetricsManager metricsManager = new MetricsManager();
        metricsManager.initMetrics();
        JVMHealthCheck healthCheck = new JVMHealthCheck(100, 100);
        //assertTrue("Health check should pass", healthCheck.execute().isHealthy());
    }
}
