package emissary.test.core.junit5;

import emissary.admin.PlaceStarter;
import emissary.command.ServerCommand;
import emissary.config.ConfigUtil;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.place.IServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.pool.MoveSpool;
import emissary.server.EmissaryServer;

import org.eclipse.jetty.server.Server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Base class of all the functional tests
 */
public abstract class FunctionalTest extends UnitTest {
    @Nullable
    protected EmissaryServer jserver = null;
    @Nullable
    protected Server jetty = null;
    @Nullable
    protected IDirectoryPlace directory = null;
    @Nullable
    protected AgentPool pool = null;
    @Nullable
    protected MoveSpool spool = null;

    public FunctionalTest() {
        super();
    }

    public FunctionalTest(String name) {
        super(name);
    }

    /**
     * Start a directory on the specified port of localhost for testing.
     */
    protected IDirectoryPlace startDirectory(int port) {
        String url = "http://localhost:" + port + "/DirectoryPlace";
        System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "" + port);
        // Instantiate the place
        IServiceProviderPlace thePlace;
        // TODO maybe uncomment
        // MDC.put(MDCConstants.SERVICE_LOCATION, "DIR-" + port);
        try {
            // No parent
            thePlace = PlaceStarter.createPlace(url, null, "emissary.directory.DirectoryPlace", null);
            logger.debug("Started directory on port {} as {}", port, thePlace);
        } finally {
            // TODO maybe uncomment
            // MDC.remove(MDCConstants.SERVICE_LOCATION);
        }
        return (IDirectoryPlace) thePlace;
    }

    /**
     * Start a localhost jetty on the specified port. Create a emissary/test123 user in a /tmp/jetty-users.properties
     */
    protected void startJetty(int port) throws Exception {
        String PROJECT_BASE = System.getenv(ConfigUtil.PROJECT_BASE_ENV);
        // Set up a password file
        File realmFile = new File(PROJECT_BASE + "/config", "jetty-users.properties");
        try (FileOutputStream ros = new FileOutputStream(realmFile)) {
            ros.write("emissary: test123, emissary".getBytes());
        }

        String nodeName = "localhost";
        String[] args = new String[] {"-b", PROJECT_BASE, "-p", Integer.toString(port), "-h", nodeName, "--mobileAgents", Integer.toString(3)};
        try {
            ServerCommand cmd = ServerCommand.parse(ServerCommand.class, args);

            jserver = EmissaryServer.init(cmd);
            jetty = jserver.startServer();
        } catch (Exception ignored) {
            // Ignore
        }

        // Wait for jetty to come up
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
            // Ignore
        }
        referenceServices(port);
    }

    /**
     * Collect local references to the emissary services
     */
    protected void referenceServices(int port) throws Exception {
        // The configuration should cause peer1 to be created.
        // Check node.cfg, node-localhost.cfg or node-localhost-8005.cfg if not
        directory = (IDirectoryPlace) Namespace.lookup("http://localhost:" + port + "/DirectoryPlace");

        try {
            pool = AgentPool.lookup();
        } catch (NamespaceException ex) {
            logger.warn("Agent pool is missing");
        }
        try {
            spool = MoveSpool.lookup();
        } catch (NamespaceException ex) {
            logger.warn("Move spool is missing");
        }
    }

    protected IServiceProviderPlace addPlace(String key, String clsName) {
        if (directory != null) {
            return addPlace(key, clsName, directory.getKey());
        } else {
            return addPlace(key, clsName, (String) null);
        }
    }

    protected IServiceProviderPlace addPlace(String key, String clsName, @Nullable String dir) {
        return PlaceStarter.createPlace(key, null, clsName, dir);
    }

    protected IServiceProviderPlace addPlace(String key, String clsName, InputStream configStream) {
        if (directory != null) {
            return PlaceStarter.createPlace(key, configStream, clsName, directory.getKey());
        } else {
            return PlaceStarter.createPlace(key, configStream, clsName, null);
        }
    }

    protected void demolishServer() {
        // Stop the places
        for (String key : Namespace.keySet()) {
            try {
                Object obj = Namespace.lookup(key);
                if (obj instanceof IServiceProviderPlace) {
                    logger.debug("Stopping {}", obj);
                    ((IServiceProviderPlace) obj).shutDown();
                }
            } catch (NamespaceException ignore) {
                // empty catch block
            }
        }

        if (directory != null && !directory.isShutdownInitiated()) {
            directory.shutDown();
            directory = null;
        }

        if (jserver != null && jserver.isServerRunning()) {
            jserver.stop();
            jetty = null;
            assertFalse(EmissaryServer.getInstance().isServerRunning(), "Server did not stop and unbind");
            jserver = null;
        }

        if (pool != null) {
            pool.close();
            pool = null;
        }

        // Clean out namespace
        for (String key : Namespace.keySet()) {
            Namespace.unbind(key);
        }

        File realmFile = new File(TMPDIR, "jetty-users.properties");
        try {
            Files.deleteIfExists(realmFile.toPath());
        } catch (IOException e) {
            logger.debug("Unable to delete temporary realmFile {}", realmFile, e);
        }
    }
}
