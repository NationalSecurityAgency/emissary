package emissary.command;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;

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
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.hamcrest.collection.IsIterableWithSize;
import org.hamcrest.junit.ExpectedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

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
        assertThat(outContent.toString(), endsWith("localhost"));
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
        assertThat(outContent.toString(), endsWith("remoteHost,remoteHost2"));
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
        assertThat(outContent.toString(), endsWith(expected));
    }

    @Test
    public void testGetPeersWithPort() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString(""), true);

        // verify
        assertThat(peers, IsIterableContainingInOrder.contains("localhost:7001", "localhost:8001", "localhost:9001"));
        assertThat(peers, IsIterableWithSize.iterableWithSize(3));
    }

    @Test
    public void testGetPeers() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString(""), false);

        // verify
        assertThat(peers, IsIterableContainingInOrder.contains("localhost"));
        assertThat(peers, IsIterableWithSize.iterableWithSize(1));
    }

    @Test
    public void testGetPeersIgnoreHost() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString("localhost"), false);

        // verify
        assertThat(peers, IsIterableWithSize.iterableWithSize(0));
    }

    @Test
    public void testGetPeersIgnoreHostAndPort() throws IOException {
        // test
        Set<String> peers = PeersCommand.getPeers(HostAndPort.fromString("localhost:8001"), true);

        // verify
        assertThat(peers, IsIterableContainingInOrder.contains("localhost:7001", "localhost:9001"));
        assertThat(peers, IsIterableWithSize.iterableWithSize(2));
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
