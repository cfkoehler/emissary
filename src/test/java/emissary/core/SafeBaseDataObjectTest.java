package emissary.core;

import emissary.core.channels.InMemoryChannelFactory;
import emissary.test.core.junit5.LogbackTester;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import static emissary.core.SafeUsageChecker.UNSAFE_MODIFICATION_DETECTED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SafeBaseDataObjectTest {

    static final byte[] DATA_MODIFICATION_BYTES = "These are the test bytes!".getBytes(StandardCharsets.US_ASCII);
    static final Level LEVELS_ONE_WARN = Level.WARN;

    @Nullable
    @SuppressWarnings("StaticAssignmentOfThrowable")
    static final Throwable NO_THROWABLES = null;
    List<LogbackTester.SimplifiedLogEvent> events = new ArrayList<>();
    LogbackTester.SimplifiedLogEvent event1 =
            new LogbackTester.SimplifiedLogEvent(LEVELS_ONE_WARN, UNSAFE_MODIFICATION_DETECTED, NO_THROWABLES);

    @Test
    void testArrayInArrayOutNoSet() throws IOException {
        try (LogbackTester logbackTester = new LogbackTester(SafeUsageChecker.class.getName())) {
            final IBaseDataObject ibdo = new SafeBaseDataObject();

            ibdo.setData(DATA_MODIFICATION_BYTES);

            final byte[] data = ibdo.data();

            Arrays.fill(data, (byte) 0);

            ibdo.checkForUnsafeDataChanges();

            assertArrayEquals(DATA_MODIFICATION_BYTES, ibdo.data());
            events.add(event1);
            logbackTester.checkLogList(events);
        }
    }

    @Test
    void shouldDetectUnsafeChangesIfArrayChangesNotFollowedByOneSet() throws IOException {
        try (LogbackTester logbackTester = new LogbackTester(SafeUsageChecker.class.getName())) {
            final IBaseDataObject ibdo = new SafeBaseDataObject();

            ibdo.setChannelFactory(InMemoryChannelFactory.create(DATA_MODIFICATION_BYTES));

            byte[] data = ibdo.data();

            Arrays.fill(data, (byte) 0);

            ibdo.checkForUnsafeDataChanges();

            assertArrayEquals(DATA_MODIFICATION_BYTES, ibdo.data());
            events.add(event1);
            logbackTester.checkLogList(events);
        }
    }

    @Test
    void shouldDetectUnsafeChangesIfArrayChangesNotFollowedByBothSet() throws IOException {
        try (LogbackTester logbackTester = new LogbackTester(SafeUsageChecker.class.getName())) {
            final IBaseDataObject ibdo = new SafeBaseDataObject();

            ibdo.setChannelFactory(InMemoryChannelFactory.create(DATA_MODIFICATION_BYTES));

            final byte[] data0 = ibdo.data();
            final byte[] data1 = ibdo.data();

            Arrays.fill(data0, (byte) 0);
            Arrays.fill(data1, (byte) 0);

            ibdo.checkForUnsafeDataChanges();

            assertArrayEquals(DATA_MODIFICATION_BYTES, ibdo.data());
            events.add(event1);
            logbackTester.checkLogList(events);
        }
    }

    @Test
    void testChannelFactoryInArrayOutNoSet() throws IOException {
        try (LogbackTester logbackTester = new LogbackTester(SafeUsageChecker.class.getName())) {
            final IBaseDataObject ibdo = new SafeBaseDataObject();

            ibdo.setChannelFactory(InMemoryChannelFactory.create(DATA_MODIFICATION_BYTES));

            final byte[] data = ibdo.data();

            Arrays.fill(data, (byte) 0);

            ibdo.checkForUnsafeDataChanges();

            assertArrayEquals(DATA_MODIFICATION_BYTES, ibdo.data());
            events.add(event1);
            logbackTester.checkLogList(events);
        }
    }

    @Test
    void shouldDetectNoUnsafeChangesImmediatelyAfterSetChannelFactory() throws IOException {
        try (LogbackTester logbackTester = new LogbackTester(SafeUsageChecker.class.getName())) {
            final IBaseDataObject ibdo = new SafeBaseDataObject();

            ibdo.setChannelFactory(InMemoryChannelFactory.create(DATA_MODIFICATION_BYTES));

            final byte[] data = ibdo.data();

            Arrays.fill(data, (byte) 0);
            ibdo.setChannelFactory(InMemoryChannelFactory.create(data));

            ibdo.checkForUnsafeDataChanges();

            assertArrayEquals(new byte[DATA_MODIFICATION_BYTES.length], ibdo.data());
            logbackTester.checkLogList(Collections.emptyList());
        }
    }

    @Test
    void shouldDetectNoUnsafeChangesImmediatelyAfterSetData() throws IOException {
        try (LogbackTester logbackTester = new LogbackTester(SafeUsageChecker.class.getName())) {
            final IBaseDataObject ibdo = new SafeBaseDataObject();

            ibdo.setChannelFactory(InMemoryChannelFactory.create(DATA_MODIFICATION_BYTES));

            final byte[] data = ibdo.data();

            Arrays.fill(data, (byte) 0);
            ibdo.setData(data);

            ibdo.checkForUnsafeDataChanges();

            assertArrayEquals(new byte[DATA_MODIFICATION_BYTES.length], ibdo.data());
            logbackTester.checkLogList(Collections.emptyList());
        }
    }

}
