package emissary.core;

import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;
import emissary.place.sample.DevNullPlace;
import emissary.test.core.junit5.UnitTest;

import com.codahale.metrics.Timer;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWatcherTest extends UnitTest {

    @Nullable
    public ResourceWatcher resourceWatcher = null;
    @Nullable
    public IServiceProviderPlace place = null;

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        // Make sure the watcher's monitor thread is gone before the next test binds a new one into
        // the Namespace, otherwise the departing thread unbinds its successor
        if (this.resourceWatcher != null) {
            this.resourceWatcher.quit();
            if (this.resourceWatcher.monitor != null) {
                this.resourceWatcher.monitor.join(TimeUnit.SECONDS.toMillis(5));
            }
            this.resourceWatcher = null;
        }
        super.tearDown();
    }

    @Test
    void testResourceWatcherWithMultipleThreads() throws IOException, InterruptedException {
        this.resourceWatcher = new ResourceWatcher();
        this.place = new DevNullPlace();
        int threadCount = 10;
        int iterations = 100;

        CountDownLatch cdl = new CountDownLatch(threadCount);
        ThreadGroup tg = new ThreadGroup("ResourceWatcherTest");
        final Thread[] pokers = new Thread[threadCount];

        // Start up threads to poke resources into the watcher
        for (int i = 0; i < threadCount; i++) {
            ResourceConsumer rc = new ResourceConsumer(iterations, 100, cdl);
            pokers[i] = new Thread(tg, rc);
        }

        for (Thread poker : pokers) {
            poker.start();
        }
        // Wait for them to be done
        cdl.await();

        // Add them up
        final Map<String, Timer> stats = this.resourceWatcher.getStats();
        assertTrue(stats.size() > 0, "Stats were not collected");

        final Timer s = stats.get("DevNullPlace");
        assertNotNull(s, "Events must be measured");

        assertEquals(threadCount * ((long) iterations), s.getCount(), "Events must not be lost");

        this.resourceWatcher.resetStats();
        assertTrue(resourceWatcher.getStats().size() > 0, "Namespaces were not preserved");
        for (Timer timer : this.resourceWatcher.getStats().values()) {
            assertEquals(0, timer.getCount(), "Stats must be cleared");
        }

        this.resourceWatcher.quit();
    }

    @Test
    void testQuitStopsMonitorThread() throws InterruptedException, NamespaceException {
        this.resourceWatcher = new ResourceWatcher();
        assertSame(this.resourceWatcher, Namespace.lookup(ResourceWatcher.DEFAULT_NAMESPACE_NAME));

        // quit() interrupts the monitor so it does not sit out the remainder of its sleep interval
        final Thread monitor = this.resourceWatcher.monitor;
        assertNotNull(monitor, "The monitor thread must be retained so quit() can interrupt it");

        this.resourceWatcher.quit();

        monitor.join(TimeUnit.SECONDS.toMillis(2));
        assertFalse(monitor.isAlive(), "Monitor thread must terminate after quit()");
        assertFalse(Namespace.exists(ResourceWatcher.DEFAULT_NAMESPACE_NAME), "Monitor thread must unbind itself on the way out");
    }

    // I was not able to get this to work by extending the current agent implementations
    // due to an uspecified issue where the thread is started during object construction
    // given the refactor forces us to operate on MobileAgent object, this was a necessity
    public final class ResourceConsumer implements IMobileAgent {
        private static final long serialVersionUID = 1L;
        final int times;
        final int duration;
        final Random rand = new Random();
        final CountDownLatch cdl;

        public ResourceConsumer(final int times, final int duration, CountDownLatch cdl) {

            this.times = times;
            this.duration = duration;
            this.cdl = cdl;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < this.times; i++) {
                    try (TimedResource tr = ResourceWatcherTest.this.resourceWatcher.starting(this, ResourceWatcherTest.this.place)) {
                        assert tr != null;
                        Thread.sleep(rand.nextInt(100));
                    } catch (InterruptedException ignored) {
                        // empty catch block
                    }
                }
            } finally {
                cdl.countDown();
            }
        }

        @Override
        public String agentId() {
            return "";
        }


        @Override
        @Nullable
        public IBaseDataObject getPayload() {
            return null;
        }

        @Override
        @Nullable
        public String getShortName() {
            return null;
        }

        @Override
        public String getPayloadCurrentForm() {
            return "";
        }

        @Override
        public void go(Object payload, IServiceProviderPlace sourcePlace) {

        }

        @Override
        public void arrive(Object payload, IServiceProviderPlace arrivalPlace, int mec, List<DirectoryEntry> iq) {

        }

        @Override
        public int getMoveErrorCount() {
            return 0;
        }


        @Override
        @Nullable
        public DirectoryEntry[] getItineraryQueueItems() {
            return null;
        }

        @Override
        public boolean isInUse() {
            return false;
        }


        @Override
        @Nullable
        public Object getPayloadForTransport() {
            return null;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getLastPlaceProcessed() {
            return "";
        }

        @Override
        public void killAgent() {

        }

        @Override
        public void killAgentAsync() {

        }

        @Override
        public boolean isZombie() {
            return true;
        }

        @Override
        public void interrupt() {

        }

        @Override
        public int getMaxMoveErrors() {
            return 0;
        }

        @Override
        public void setMaxMoveErrors(int value) {

        }

        @Override
        public int getMaxItinerarySteps() {
            return 10;
        }

        @Override
        public void setMaxItinerarySteps(int value) {

        }
    }
}
