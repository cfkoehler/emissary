package emissary.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.google.common.net.HostAndPort;
import emissary.config.ConfigUtil;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PeersCommandIT extends UnitTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    private PeersCommand command;
    private List<String> arguments;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        arguments = new ArrayList<>();
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        arguments.clear();
        outContent = null;
        errContent = null;
        command = null;
    }

    @Test
    public void testDefaultPeer() throws Exception {
        // setup
        command = PeersCommand.parse(PeersCommand.class, arguments);

        // test
        captureStdOutAndStdErrAndRunCommand(command);

        // verify
        assertTrue(outContent.toString().endsWith("localhost"));
    }

    @Test
    public void testIgnoreHostAndFlavor() throws Exception {
        // setup
        // needed because it has already been initialize as a static
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTING");
        emissary.config.ConfigUtil.initialize();
        arguments.addAll(Arrays.asList("--flavor", "TESTING", "-ih", "localhost"));
        command = PeersCommand.parse(PeersCommand.class, arguments);

        // test
        captureStdOutAndStdErrAndRunCommand(command);

        // verify
        assertTrue(outContent.toString().endsWith("remoteHost,remoteHost2"));
    }

    @Test
    public void testDelimiter() throws Exception {
        // setup
        // needed because it has already been initialize as a static
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTING");
        emissary.config.ConfigUtil.initialize();
        arguments.addAll(Arrays.asList("--flavor", "TESTING", "-d", "\\n"));
        command = PeersCommand.parse(PeersCommand.class, arguments);
        String newLine = System.getProperty("line.separator");
        String expected = "localhost" + newLine + "remoteHost" + newLine + "remoteHost2";

        // test
        captureStdOutAndStdErrAndRunCommand(command);

        // verify
        assertTrue(outContent.toString().endsWith(expected));
    }

    @Test
    public void testGetPeersWithPort() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString(""), true);

        // verify
        assertIterableEquals(Arrays.asList("localhost:7001", "localhost:8001", "localhost:9001"), peers);
        assertEquals(3, peers.size());
    }

    @Test
    public void testGetPeers() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString(""), false);

        // verify
        assertTrue(peers.contains("localhost"));
        assertEquals(1, peers.size());
    }

    @Test
    public void testGetPeersIgnoreHost() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString("localhost"), false);

        // verify
        assertTrue(peers.isEmpty());
    }

    @Test
    public void testGetPeersIgnoreHostAndPort() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString("localhost:8001"), true);

        // verify
        assertIterableEquals(Arrays.asList("localhost:7001", "localhost:9001"), peers);
        assertEquals(2, peers.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPeersBadPort() throws IOException {
        PeersCommand.getPeers(HostAndPort.fromString("localhost:1234567890"), true);
    }

    private void captureStdOutAndStdErrAndRunCommand(PeersCommand cmd) {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        cmd.run(new JCommander());
        System.setOut(origOut);
        System.setErr(origErr);
    }

}
