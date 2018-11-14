package emissary.test.core;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import emissary.admin.PlaceStarter;
import emissary.command.ServerCommand;
import emissary.config.ConfigUtil;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.place.IServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.pool.MoveSpool;
import emissary.server.EmissaryServer;
import org.eclipse.jetty.server.Server;

/**
 * Base class of all the functional tests
 */
public class FunctionalTest extends UnitTest {
    protected EmissaryServer jserver = null;
    protected Server jetty = null;
    protected IDirectoryPlace directory = null;
    protected AgentPool pool = null;
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
        IServiceProviderPlace thePlace = null;
        // TODO maybe uncomment
        // MDC.put(MDCConstants.SERVICE_LOCATION, "DIR-" + port);
        try {
            // No parent
            thePlace = PlaceStarter.createPlace(url, (InputStream) null, "emissary.directory.DirectoryPlace", null);
            logger.debug("Started directory on port " + port + " as " + thePlace);
        } finally {
            // TODO maybe uncomment
            // MDC.remove(MDCConstants.SERVICE_LOCATION);
        }
        return (IDirectoryPlace) thePlace;
    }

    /**
     * Start a localhost jetty on the specified port using jetty.xml in the local package
     */
    protected void startJetty(int port) throws Exception {
        startJetty(port, "jetty");
    }

    /**
     * Start a localhost jetty on the specified port Create a emissary/test123 user in a /tmp/jetty-users.properties but use
     * the runtime package "xrez".xml file
     */
    protected void startJetty(int port, String xrez) throws Exception {
        String PROJECT_BASE = System.getenv(ConfigUtil.PROJECT_BASE_ENV);
        // Set up a password file
        File realmFile = new File(PROJECT_BASE + "/config", "jetty-users.properties");
        FileOutputStream ros = new FileOutputStream(realmFile);
        ros.write("emissary: test123, emissary".getBytes());
        ros.close();

        String nodeName = "localhost";
        // System.setProperty(EmissaryNode.NODE_NAME_PROPERTY, nodeName);
        // System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "" + port);
        // ResourceReader rr = new ResourceReader();
        // String xmlName = rr.getXmlName(thisPackage, xrez);
        // Resource jettyxml = Resource.newSystemResource(xmlName);
        String[] args = new String[] {"-b", PROJECT_BASE, "-p", Integer.toString(port), "-h", nodeName, "--mobileAgents", Integer.toString(3)};
        try {
            ServerCommand cmd = ServerCommand.parse(ServerCommand.class, args);

            jserver = new emissary.server.EmissaryServer(cmd);
            jetty = jserver.startServer();
        } catch (Exception e) {

        }

        // Wait for jetty to come up
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            // empty catch block
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
        } catch (emissary.core.NamespaceException ex) {
            logger.warn("Agent pool is missing");
        }
        try {
            spool = MoveSpool.lookup();
        } catch (emissary.core.NamespaceException ex) {
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

    protected IServiceProviderPlace addPlace(String key, String clsName, String dir) {
        return PlaceStarter.createPlace(key, (InputStream) null, clsName, dir);
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
                    logger.debug("Stopping " + obj);
                    ((IServiceProviderPlace) obj).shutDown();
                }
            } catch (emissary.core.NamespaceException ignore) {
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
            assertTrue("Server did not stop and unbind", !EmissaryServer.isStarted());
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
        if (realmFile.exists()) {
            if (!realmFile.delete()) {
                logger.debug("Unable to delete temporary realmFile " + realmFile);
            }
        }
    }
}
