package emissary.core;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.place.IServiceProviderPlace;
import emissary.place.sample.CachePlace;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.test.core.junit5.FunctionalTest;
import emissary.util.Version;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import javax.annotation.Nullable;

import static emissary.core.Form.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FTestMovingAgent extends FunctionalTest {
    private IDirectoryPlace dir1 = null;
    private IDirectoryPlace dir2 = null;
    private IServiceProviderPlace toUpper = null;
    private IServiceProviderPlace toLower = null;
    private CachePlace cache = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
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
    @AfterEach
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

    @Test
    void testMobility() {
        // This will run as local calls with no moves
        runTest(HDMobileAgent.class.getName());
        runTest(MobileAgent.class.getName());

        // These agents force moves even though
        // the places are in the same namespace
        runTest(BogusHDAgent.class.getName());

        // Test JSP actions
        runHttpTest();
    }

    private void runTest(@Nullable final String agentClass) {
        assertNotNull(this.dir1, "Directory1 should have been set up");
        assertNotNull(this.dir2, "Directory2 should have been set up");
        assertNotNull(this.toLower, "toLower place should have been set up");
        assertNotNull(this.toUpper, "toUpper place should have been set up");
        assertNotNull(this.cache, "cache place should have been set up");

        if (agentClass != null && !pool.getClassName().equals(agentClass)) {
            logger.debug("Resetting factory from " + pool.getClassName() + " to " + agentClass);
            pool.resetFactory(new MobileAgentFactory(agentClass));
            try {
                pool = AgentPool.lookup();
                logger.debug("Found pool after reset to " + agentClass);
                spool.resetPool();
            } catch (emissary.core.NamespaceException ex) {
                fail("Agent pool is missing after reset to " + agentClass, ex);
            }
        } else if (agentClass != null) {
            logger.debug("Factory already using " + agentClass);
        }

        // Create a payload and send it to the spool for UpperPlace
        IBaseDataObject payload = DataObjectFactory.getInstance(new Object[] {"abcdefghijklmnopqrstuvwxyz".getBytes(), "test_load", "LOWER_CASE"});
        payload.setFileType(TEXT);

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
        assertTrue(this.cache.getCacheSize() > 0, "Payload should arrive at cache using " + agentClass);

        payload = this.cache.pop();
        assertNotNull(payload, "Payload coming from cache");

        assertEquals("FINI", payload.currentForm(), "Current form on payload");
        final List<String> th = payload.transformHistory();
        assertTrue(th.size() > 5, "Transform history");


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
        assertEquals(HttpStatus.SC_OK, ws.getStatus(), "welcome.jsp must return good status");
        assertNotNull(msg, "welcome.jsp must have text");
        assertTrue(msg.contains(version.toString()), "welcome.jsp contains version");

        get = new HttpGet(urlBase + "Namespace.action");
        ws = h.send(get);
        msg = ws.getContentString();
        assertEquals(HttpStatus.SC_OK, ws.getStatus(), "Namespace.action must return good status");
        assertNotNull(msg, "Namespace text must not be mull");
        assertTrue(msg.contains("MoveSpool"), "Namespace must contain MoveSpool");
        assertTrue(msg.contains("AgentPool"), "Namespace must contain AgentPool");
        assertTrue(msg.contains("Directory"), "Namespace must contain directory");
        assertTrue(msg.contains("CachePlace"), "Namespace must have place name");

        get = new HttpGet(urlBase + "DumpDirectory.action");
        ws = h.send(get);
        msg = ws.getContentString();
        assertEquals(HttpStatus.SC_OK, ws.getStatus(), "DumpDirectory.action must return good status");
        assertNotNull(msg, "DumpDirectory must have text");
        assertTrue(msg.contains("CachePlace"), "DumpDirectory must have place name");

    }
}
