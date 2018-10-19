package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import emissary.config.ConfigUtil;
import emissary.core.BaseDataObject;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.directory.DirectoryEntry;
import emissary.directory.KeyManipulator;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServiceProviderPlaceTest extends UnitTest {
    private IServiceProviderPlace place = null;

    private static final byte[] configData = ("PLACE_NAME = \"PlaceTest\"\n" + "SERVICE_NAME = \"TEST_SERVICE_NAME\"\n"
            + "SERVICE_TYPE = \"ANALYZE\"\n" + "SERVICE_DESCRIPTION = \"test place\"\n" + "SERVICE_COST = 60\n" + "SERVICE_QUALITY = 90\n"
            + "SERVICE_PROXY = \"TEST_SERVICE_PROXY\"\n").getBytes();

    private static final byte[] configDataWithResourceLimit = ("PLACE_NAME = \"PlaceTest\"\n" + "SERVICE_NAME = \"TEST_SERVICE_NAME\"\n"
            + "SERVICE_TYPE = \"ANALYZE\"\n" + "SERVICE_DESCRIPTION = \"test place\"\n" + "SERVICE_COST = 60\n" + "SERVICE_QUALITY = 90\n"
            + "PLACE_RESOURCE_LIMIT_MILLIS = 10\n" + "SERVICE_PROXY = \"TEST_SERVICE_PROXY\"\n").getBytes();

    private static final byte[] configDataMissingCost = ("PLACE_NAME = \"PlaceTest\"\n" + "SERVICE_NAME = \"TEST_SERVICE_NAME\"\n"
            + "SERVICE_TYPE = \"ANALYZE\"\n" + "SERVICE_DESCRIPTION = \"test place\"\n" + "SERVICE_QUALITY = 90\n"
            + "SERVICE_PROXY = \"TEST_SERVICE_PROXY\"\n").getBytes();

    private static final byte[] configDataMissingType = ("PLACE_NAME = \"PlaceTest\"\n" + "SERVICE_NAME = \"TEST_SERVICE_NAME\"\n"
            + "SERVICE_DESCRIPTION = \"test place\"\n" + "SERVICE_COST = 60\n" + "SERVICE_QUALITY = 90\n"
            + "SERVICE_PROXY = \"TEST_SERVICE_PROXY\"\n").getBytes();

    private static final byte[] configDataMissingProxy = ("PLACE_NAME = \"PlaceTest\"\n" + "SERVICE_NAME = \"TEST_SERVICE_NAME\"\n"
            + "SERVICE_DESCRIPTION = \"test place\"\n" + "SERVICE_COST = 60\n" + "SERVICE_QUALITY = 90\n").getBytes();

    private static final byte[] configKeyData = ("TGT_HOST = \"localhost\"\n" + "TGT_PORT = \"8001\"\n"
            + "SERVICE_KEY = \"TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050\"\n" + "SERVICE_DESCRIPTION = \"test place\"\n")
                    .getBytes();

    private static final byte[] configKeysData = ("TGT_HOST = \"localhost\"\n" + "TGT_PORT = \"8001\"\n"
            + "SERVICE_KEY = \"TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050\"\n"
            + "SERVICE_KEY = \"TP2.TNAME.TRANSFORM.http://@{TGT_HOST}:@{TGT_PORT}/TP2PlaceName$6050\"\n"
            + "SERVICE_KEY = \"TP3.TNAME.ANALYZE.http://@{TGT_HOST}:@{TGT_PORT}/TP3PlaceName$7050\"\n"
            + "SERVICE_KEY = \"TP4.TNAME.IO.http://@{TGT_HOST}:@{TGT_PORT}/TP4PlaceName$8050\"\n" + "SERVICE_DESCRIPTION = \"test place\"\n")
                    .getBytes();

    private static final byte[] configNoKeysData = ("TGT_HOST = \"localhost\"\n" + "TGT_PORT = \"8001\"\n" + "SERVICE_DESCRIPTION = \"bogus\"\n")
            .getBytes();

    private static final byte[] configBadKeyData = ("TGT_HOST = \"localhost\"\n" + "TGT_PORT = \"8001\"\n"
            + "SERVICE_KEY = \"TP4.TNAME.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$8050\"\n" + "SERVICE_DESCRIPTION = \"bogus\"\n").getBytes();

    String CFGDIR = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);

    @Override
    @Before
    public void setUp() throws Exception {

        InputStream config = new ByteArrayInputStream(configData);
        place = new PlaceTest(config, null, "http://localhost:8001/PlaceTest");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
    }

    @Test
    public void testConfiguration() {
        assertNotNull("Place created and configured", place);
        assertEquals("Configured place name", "PlaceTest", place.getPlaceName());
        assertEquals("Primary proxy", "TEST_SERVICE_PROXY", place.getPrimaryProxy());
        assertEquals("Key generation", "TEST_SERVICE_PROXY.TEST_SERVICE_NAME.ANALYZE.http://localhost:8001/PlaceTest", place.getKey());
        DirectoryEntry de = place.getDirectoryEntry();
        assertNotNull("Directory entry", de);
        assertEquals("Cost in directory entry", 60, de.getCost());
        assertEquals("Quality in directory entry", 90, de.getQuality());
        assertEquals("Description in directory entry", "test place", de.getDescription());

        place.addServiceProxy("FOO");
        place.addServiceProxy("BAR");
        Set<String> proxies = place.getProxies();
        assertNotNull("Proxies as a set", proxies);
        assertEquals("Size of proxies set", 3, proxies.size());
        assertTrue("Proxies contains original in set", proxies.contains("TEST_SERVICE_PROXY"));
        assertTrue("Proxies contains new in set", proxies.contains("FOO"));
        assertTrue("Proxies contains last in set", proxies.contains("BAR"));
    }

    @Test
    public void testNukem() {
        place.addServiceProxy("FOO-*");
        place.addServiceProxy("BAR(*)");

        IBaseDataObject d = new BaseDataObject();
        d.pushCurrentForm("TEST_SERVICE_PROXY");

        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Remove default form", 0, d.currentFormSize());

        d.pushCurrentForm("BAZ");
        d.pushCurrentForm("TEST_SERVICE_PROXY");
        d.pushCurrentForm("QUUZ");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Remove default form leaving others", 2, d.currentFormSize());

        d.replaceCurrentForm(null);

        d.pushCurrentForm("FOO-WILD");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Remove dash-wild form", 0, d.currentFormSize());

        d.pushCurrentForm("BAR(WILD)");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Remove paren-wild form", 0, d.currentFormSize());

        d.pushCurrentForm("FOO(WILD)");
        d.pushCurrentForm("BAR-WILD");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Do not remove on dash-paren cross wild", 2, d.currentFormSize());
        d.replaceCurrentForm(null);

        d.pushCurrentForm("FOO-BAR-BAZ");
        d.pushCurrentForm("FOO-BAR-BAR(WILD)");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Remove multi layered wild", 0, d.currentFormSize());

        d.pushCurrentForm("QUUZ(BANG)");
        d.pushCurrentForm("BANG(QUUZ)-FOO(BAR)");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Do not remove on dash-paren cross wild", 2, d.currentFormSize());
        d.pushCurrentForm("TEST_SERVICE_PROXY");
        place.addServiceProxy("*");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals("Remove all on full wild", 0, d.currentFormSize());
    }

    @Test
    public void testDuplicateProxy() {
        Set<String> proxies = place.getProxies();
        assertEquals("Place original proxy count ", 1, proxies.size());
        place.addServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals("Place proxy count after addServiceProxy", 2, proxies.size());
        place.addServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals("Place proxy count after duplicate addServcieProxy", 2, proxies.size());

        place.removeServiceProxy("BAR");
        proxies = place.getProxies();
        assertEquals("Place proxy count after remove bogus entry", 2, proxies.size());

        place.removeServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals("Place proxy count after removeServiceProxy", 1, proxies.size());

        place.removeServiceProxy("TEST_SERVICE_PROXY");
        proxies = place.getProxies();
        assertNotNull("No proxy place returns empty set not null", proxies);
        assertEquals("Place has all proxies removed!", 0, proxies.size());

        place.addServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals("Place has 1 proxies after add onto an empty", 1, proxies.size());
    }

    @Test
    public void testLocOnlyConstructorWithResourceConfigData() {
        try {
            MyStreamConfigedTestPlace mtp = new MyStreamConfigedTestPlace("http://example.com:8001/MyStreamConfigedTestPlace");

            assertTrue("MyStreamConfigedTestPlace did not initialize", mtp.getFinishedSuperConstructor());

            mtp.shutDown();
        } catch (IOException iox) {
            fail("Could not initialize MyStreamConfigedTestPlace with one arg constructor: " + iox);
        }
    }

    @Test
    public void testLocOnlyConstructorWithFileConfigDataAndNoPackage() throws EmissaryException {
        runFileConfiguredTest(false, 1);
    }

    @Test
    public void testLocOnlyConstructorWithFileConfigDataAndPackage() throws EmissaryException {
        runFileConfiguredTest(true, 1);
    }

    private void runFileConfiguredTest(boolean usePackage, int ctorType) throws EmissaryException {
        File cfg = null;
        FileOutputStream fos = null;
        try {
            // set config to java.io.tmpdir, no package resources
            // setConfig(System.getProperty("java.io.tmpdir", "."), false);

            // Write out the config data to the temp config dir
            if (usePackage) {
                cfg = new File(CFGDIR + "/" + thisPackage.getName() + ".MyFileConfigedTestPlace" + ConfigUtil.CONFIG_FILE_ENDING);
            } else {
                cfg = new File(CFGDIR + "/MyFileConfigedTestPlace" + ConfigUtil.CONFIG_FILE_ENDING);
            }
            fos = new FileOutputStream(cfg);
            fos.write(configData);

            MyFileConfigedTestPlace mtp = null;

            if (ctorType == 1) {
                // No config specified, auto-discovered
                mtp = new MyFileConfigedTestPlace("http://example.com:8001/MyFileConfigedTestPlace");
            } else if (ctorType == 2) {
                // String name of config file
                mtp = new MyFileConfigedTestPlace(cfg.getName(), null, "http://example.com:8001/MyTestPlace");
            } else if (ctorType == 3) {
                // Stream input of config
                InputStream config = new ByteArrayInputStream(configData);
                mtp = new MyFileConfigedTestPlace(config, null, "http://example.com:8001/MyTestPlace");
            }

            assertNotNull("Test run with bad ctypeType arg?", mtp);

            assertTrue("MyFileConfigedTestPlace did not initialize", mtp.getFinishedSuperConstructor());

            mtp.shutDown();
        } catch (IOException iox) {
            fail("Could not initialize MyFileConfigedTestPlace with one arg constructor: " + iox);
        } finally {
            // Clean up the tmp config settings
            restoreConfig();

            cfg.delete();

            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignore) {
                }
            }
            if (cfg != null && cfg.exists()) {
                cfg.delete();
            }
        }
    }

    @Test
    public void testRequireOfCostParameter() {
        try {
            InputStream config = new ByteArrayInputStream(configDataMissingCost);
            new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
            fail("Should have required SERIVICE_COST in config stream");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testMBeanInteration() throws Exception {
        InputStream config = new ByteArrayInputStream(configDataWithResourceLimit);
        place = new PlaceTest(config, null, "http://example.com:8001/PlaceTest");

        if (place instanceof ServiceProviderPlaceMBean) {
            List<String> a = ((ServiceProviderPlaceMBean) place).getRunningConfig();
            assertTrue("Running config must have some entries", a.size() > 0);
            String stats = ((ServiceProviderPlaceMBean) place).getPlaceStats();
            assertNotNull("Stats expected", stats);
            long resourceLimit = ((ServiceProviderPlaceMBean) place).getResourceLimitMillis();
            assertEquals("Resource limit must be saved and returned", 10, resourceLimit);

            // These send output to the logger, just verify that they don't npe, they are
            // slightly useful to keep around for using in jconsole
            ((ServiceProviderPlaceMBean) place).dumpPlaceStats();
            ((ServiceProviderPlaceMBean) place).dumpRunningConfig();

        } else {
            fail("Place should be an instance of ServiceProviderPlaceMBean");
        }
    }


    @Test
    public void testWithMissingPlaceLocation() throws Exception {
        InputStream config = new ByteArrayInputStream(configData);
        IServiceProviderPlace p = new PlaceTest(config, null, null);
        assertNotNull("Place should be created with default location", p);
        assertTrue("Place default location should be in key", p.getKey().indexOf("/PlaceTest") > -1);

        config = new ByteArrayInputStream(configData);
        p = new PlaceTest(config, null);
        assertNotNull("Place should be created with default location", p);
        assertTrue("Place default location should be in key", p.getKey().indexOf("/PlaceTest") > -1);

        config = new ByteArrayInputStream(configData);
        p = new PlaceTest(config);
        assertNotNull("Place should be created with default location", p);
        assertTrue("Place default location should be in key", p.getKey().indexOf("/PlaceTest") > -1);

        config = new ByteArrayInputStream(configData);
        p = new PlaceTest(config);
        assertNotNull("Place should be created with default location", p);
        assertTrue("Place default location should be in key", p.getKey().indexOf("/PlaceTest") > -1);
    }

    @Test
    public void testRequireOfTypeParameter() {
        try {
            InputStream config = new ByteArrayInputStream(configDataMissingType);
            new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
            fail("Should have required SERVICE_TYPE in config stream");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testRequireOfProxyOrKey() {
        try {
            InputStream config = new ByteArrayInputStream(configDataMissingProxy);
            new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
            fail("Should have required SERVICE_PROXY or SERVICE_KEY in config stream");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testServiceKeyConfig() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
            assertEquals("Configured place name", "TPlaceName", tp.getPlaceName());
            assertEquals("Primary proxy", "TPROXY", tp.getPrimaryProxy());
            assertEquals("Key generation", "TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey());
            DirectoryEntry de = tp.getDirectoryEntry();
            assertNotNull("Directory entry", de);
            assertEquals("Cost in directory entry", 50, de.getCost());
            assertEquals("Quality in directory entry", 50, de.getQuality());
            assertEquals("Description in directory entry", "test place", de.getDescription());

            Set<String> keys = tp.getKeys();
            assertNotNull("Keys as a set", keys);
            assertEquals("Size of Keys", 1, keys.size());

            Set<String> proxies = tp.getProxies();
            assertNotNull("Proxies as a set", proxies);
            assertEquals("Size of proxies set", 1, proxies.size());
            assertTrue("Proxies contains original in set", proxies.contains("TPROXY"));
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY: " + iox.getMessage());
        }
    }

    @Test
    public void testKeyConfigAddKey() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
            assertEquals("Configured place name", "TPlaceName", tp.getPlaceName());

            tp.addKey("TPROXY2.TNAME.IO.http://localhost:8001/TPlaceName$6050");

            Set<String> keys = tp.getKeys();
            assertNotNull("Keys as a set", keys);
            assertEquals("Size of Keys", 2, keys.size());

            assertEquals("Key generation uses first on list", "TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey());

            // Add a bad key
            tp.addKey("TPROXY3.TNAME.http://localhost:8001/TPlaceName$6050");
            keys = tp.getKeys();
            assertNotNull("Keys as a set after bad key add", keys);
            assertEquals("Size of Keys after bad key add", 2, keys.size());


            Set<String> proxies = tp.getProxies();
            assertNotNull("Proxies as a set", proxies);
            assertEquals("Size of proxies set", 2, proxies.size());
            assertTrue("Proxies contains original in set", proxies.contains("TPROXY"));
            assertTrue("Proxies contains original in set", proxies.contains("TPROXY2"));
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY: " + iox.getMessage());
        }
    }

    @Test
    public void testKeyConfigPlaceNameCtor() {
        try {
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("Configured place name", "TPlaceName", tp.getPlaceName());
            assertEquals("Key generation uses first on list", "TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey());

            Set<String> keys = tp.getKeys();
            assertNotNull("Keys as a set", keys);
            assertEquals("Size of Keys", 1, keys.size());

        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY: " + iox.getMessage());
        }
    }

    @Test
    public void testKeyConfigShortPlaceName() {
        try {
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config, null, "PlaceTest");
            assertEquals("Configured place name", "TPlaceName", tp.getPlaceName());
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY: " + iox.getMessage());
        }
    }

    @Test
    public void testNoKeyInConfig() {
        try {
            InputStream config = new ByteArrayInputStream(configNoKeysData);
            new PlaceTest(config);
            fail("Should have failed with no keys configured");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testBadKeyInConfig() {
        try {
            InputStream config = new ByteArrayInputStream(configBadKeyData);
            new PlaceTest(config);
            fail("Should have failed with bad key in config");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testNoArgCtor() throws EmissaryException {
        // Point config to this package to find default config
        setConfig(null, true);
        try {
            PlaceTest tp = new PlaceTest();
            assertEquals("Configured place name", "TestFooPlace", tp.getPlaceName());
        } catch (IOException iox) {
            fail("Place should have configured with no arg ctor: " + iox.getMessage());
        } finally {
            restoreConfig();
        }
    }

    @Test
    public void testKeysConfig() {
        try {
            // SERVICE_KEY = "TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050"
            // SERVICE_KEY = "TP2.TNAME.TRANSFORM.http://@{TGT_HOST}:@{TGT_PORT}/TP2PlaceName$6050"
            // SERVICE_KEY = "TP3.TNAME.ANALYZE.http://@{TGT_HOST}:@{TGT_PORT}/TP3PlaceName$7050"
            // SERVICE_KEY = "TP4.TNAME.IO.http://@{TGT_HOST}:@{TGT_PORT}/TP4PlaceName$8050"
            InputStream config = new ByteArrayInputStream(configKeysData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("Configured place name", "TPlaceName", tp.getPlaceName());
            assertEquals("Primary proxy", "TPROXY", tp.getPrimaryProxy());
            assertEquals("Key generation uses first key", "TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey());

            // Check directory entries
            DirectoryEntry de = tp.getDirectoryEntry();
            assertNotNull("Directory entry", de);
            assertEquals("Cost in directory entry", 50, de.getCost());
            assertEquals("Quality in directory entry", 50, de.getQuality());
            assertEquals("Description in directory entry", "test place", de.getDescription());

            // Check keys
            Set<String> keys = tp.getKeys();
            assertNotNull("Keys as a set", keys);
            assertEquals("Size of Keys", 4, keys.size());

            // Check namespace
            for (String k : keys) {
                String sl = KeyManipulator.getServiceLocation(k);
                try {
                    Object place = Namespace.lookup(sl);
                    assertNotNull("Place must be found by service location", place);
                    assertTrue("Place bound by service location must be correct object", place == tp);
                } catch (Exception ex) {
                    fail("Should have found " + sl + " in namespace");
                }
            }

            // Check proxies
            Set<String> proxies = tp.getProxies();
            assertNotNull("Proxies as a set", proxies);
            assertEquals("Size of proxies set", 4, proxies.size());
            assertTrue("Proxies contains original in set", proxies.contains("TPROXY"));
            assertTrue("Proxies contains original in set", proxies.contains("TP2"));
            assertTrue("Proxies contains original in set", proxies.contains("TP3"));
            assertTrue("Proxies contains original in set", proxies.contains("TP4"));
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY: " + iox.getMessage());
        }
    }

    @Test
    public void testKeyRemoval() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("Configured place name", "TPlaceName", tp.getPlaceName());
            tp.removeKey("TPROXY.TNAME.ID.http://localhost:8001/TPlaceName$5050");
            assertEquals("Primary proxy empty after key removal", "", tp.getPrimaryProxy());
            assertEquals("Key size after removal must be 0", 0, tp.getKeys().size());
            assertEquals("Proxy size after removal must be 0", 0, tp.getProxies().size());
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY: " + iox.getMessage());
        }
    }

    @Test
    public void testBogusKeyRemoval() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("Configured place name", "TPlaceName", tp.getPlaceName());
            tp.removeKey("TPROXY.TNAME.ANALYZE.http://localhost:8001/TPlaceName$5050");
            assertEquals("Primary proxy present after bogus key removal", "TPROXY", tp.getPrimaryProxy());
            assertEquals("Key size after bogus removal must be 1", 1, tp.getKeys().size());
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY: " + iox.getMessage());
        }
    }

    private static final class PlaceTest extends ServiceProviderPlace {

        public PlaceTest() throws IOException {
            super();
        }

        public PlaceTest(InputStream config) throws IOException {
            super(config);
        }

        public PlaceTest(InputStream config, String loc) throws IOException {
            super(config, loc);
        }

        public PlaceTest(InputStream config, String dir, String loc) throws IOException {
            super(config, dir, loc);
        }

        @Override
        public void process(IBaseDataObject d) {
            assertNotNull(d);
        }

        public void testNukeMyProxies(IBaseDataObject d) {
            nukeMyProxies(d);
        }

    }
}
