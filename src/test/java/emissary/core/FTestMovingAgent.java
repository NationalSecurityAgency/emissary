package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.place.IServiceProviderPlace;
import emissary.place.sample.CachePlace;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.test.core.FunctionalTest;
import emissary.util.Version;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Before;

public class FTestMovingAgent extends FunctionalTest {
    private IDirectoryPlace dir1 = null;
    private IDirectoryPlace dir2 = null;
    private IServiceProviderPlace toUpper = null;
    private IServiceProviderPlace toLower = null;
    private CachePlace cache = null;

    @Override
    @Before
    public void setUp() throws Exception {
        setConfig(System.getProperty("java.io.tmpdir", "."), true);

        logger.debug("Starting Mobility tests");

        // start jetty
        startJetty(8005);

        this.dir1 = directory; // setup in super class
        this.dir2 = startDirectory(9005);
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            // empty catch block
        }

        // Start a toLower and toUpper
        this.toLower = addPlace("http://localhost:8005/ToLowerPlace", "emissary.place.sample.ToLowerPlace");

        this.toUpper = addPlace("http://localhost:9005/ToUpperPlace", "emissary.place.sample.ToUpperPlace", this.dir2.getKey());

        this.cache = (CachePlace) addPlace("http://localhost:9005/CachePlace", "emissary.place.sample.CachePlace", this.dir2.getKey());

        // Force them to hook up now
        this.dir1.heartbeatRemoteDirectory(this.dir2.getKey());
        this.dir2.heartbeatRemoteDirectory(this.dir1.getKey());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        logger.debug("Starting tearDown phase");

        this.cache.shutDown();
        this.dir2.shutDown();
        this.cache = null;
        this.dir2 = null;
        this.dir1 = null;

        demolishServer();
        restoreConfig();

    }

    @org.junit.Test
    public void testMobility() {
        // This will run as local calls with no moves
        runTest(HDMobileAgent.class.getName());
        runTest(MobileAgent.class.getName());

        // These agents force moves even though
        // the places are in the same namespace
        runTest(BogusHDAgent.class.getName());

        // Test JSP actions
        runHttpTest();
    }

    private void runTest(final String agentClass) {
        assertNotNull("Directory1 should have been set up", this.dir1);
        assertNotNull("Directory2 should have been set up", this.dir2);
        assertNotNull("toLower place should have been set up", this.toLower);
        assertNotNull("toUpper place should have been set up", this.toUpper);
        assertNotNull("cache place should have been set up", this.cache);

        if (agentClass != null && !pool.getClassName().equals(agentClass)) {
            logger.debug("Resetting factory from " + pool.getClassName() + " to " + agentClass);
            pool.resetFactory(new MobileAgentFactory(agentClass));
            try {
                pool = AgentPool.lookup();
                logger.debug("Found pool after reset to " + agentClass);
                spool.resetPool();
            } catch (emissary.core.NamespaceException ex) {
                fail("Agent pool is missing after reset to " + agentClass);
            }
        } else if (agentClass != null) {
            logger.debug("Factory already using " + agentClass);
        }

        // Create a payload and send it to the spool for UpperPlace
        IBaseDataObject payload = DataObjectFactory.getInstance(new Object[] {"abcdefghijklmnopqrstuvwxyz".getBytes(), "test_load", "LOWER_CASE"});
        payload.setFileType("TEXT");

        spool.send(payload);

        // Wait up to 10 seconds in 1/4 second increments
        int waitCounter = 0;
        while (this.cache.getCacheSize() == 0 && waitCounter < 40) {
            try {
                Thread.sleep(250);
            } catch (Exception ex) {
                // empty catch block
            }
            waitCounter++;
        }

        // Check agent arrived at cache
        assertTrue("Payload should arrive at cache using " + agentClass, this.cache.getCacheSize() > 0);

        payload = this.cache.pop();
        assertNotNull("Payload coming from cache", payload);

        assertEquals("Current form on payload", "FINI", payload.currentForm());
        final List<String> th = payload.transformHistory();
        assertTrue("Transform history", th.size() > 5);


        // Purge the cache for the next test
        payload = null;
        do {
            payload = this.cache.pop();
        } while (payload != null);
    }

    private void runHttpTest() {
        final Version version = new Version();
        final EmissaryClient h = new EmissaryClient();
        final String urlBase = KeyManipulator.getServiceHostURL(this.dir1.getKey()) + EmissaryClient.CONTEXT + "/";
        HttpGet get = new HttpGet(urlBase + "welcome.jsp");
        EmissaryResponse ws = h.send(get);
        String msg = ws.getContentString();
        // TODO consider moving this into the EmissaryResponse ((ws.getStatus() == HttpStatus.SC_OK)))
        assertTrue("welcome.jsp must return good status", (ws.getStatus() == HttpStatus.SC_OK));
        assertNotNull("welcome.jsp must have text", msg);
        assertTrue("welcome.jsp contains version", msg.indexOf(version.toString()) > -1);

        get = new HttpGet(urlBase + "Namespace.action");
        ws = h.send(get);
        msg = ws.getContentString();
        assertTrue("Namespace.action must return good status", (ws.getStatus() == HttpStatus.SC_OK));
        assertNotNull("Namespace text must not be mull", msg);
        assertTrue("Namespace must contain MoveSpool", msg.indexOf("MoveSpool") > -1);
        assertTrue("Namespace must contain AgentPool", msg.indexOf("AgentPool") > -1);
        assertTrue("Namespace must contain directory", msg.indexOf("Directory") > -1);
        assertTrue("Namespace must have place name", msg.indexOf("CachePlace") > -1);

        get = new HttpGet(urlBase + "DumpDirectory.action");
        ws = h.send(get);
        msg = ws.getContentString();
        assertTrue("DumpDirectory.action must return good status", (ws.getStatus() == HttpStatus.SC_OK));
        assertNotNull("DumpDirectory must have text", msg);
        assertTrue("DumpDirectory must have place name", msg.indexOf("CachePlace") > -1);

    }
}
