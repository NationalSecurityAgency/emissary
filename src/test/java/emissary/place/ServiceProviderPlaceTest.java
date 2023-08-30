package emissary.place;

import emissary.config.ConfigUtil;
import emissary.core.BaseDataObject;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.directory.DirectoryEntry;
import emissary.directory.KeyManipulator;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ServiceProviderPlaceTest extends UnitTest {
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

    private static final byte[] configDeniedData = ("PLACE_NAME = \"PlaceTest\"\n" + "SERVICE_NAME = \"TEST_SERVICE_NAME\"\n"
            + "SERVICE_TYPE = \"ANALYZE\"\n" + "SERVICE_DESCRIPTION = \"test place with denied list\"\n" + "SERVICE_COST = 60\n"
            + "SERVICE_QUALITY = 90\n" + "SERVICE_PROXY = \"TEST_SERVICE_PROXY\"\n" + "SERVICE_PROXY = \"TEST_SERVICE_PROXY2\"\n"
            + "SERVICE_PROXY_DENY = \"TEST_SERVICE_PROXY\"\n" + "SERVICE_PROXY_DENY = \"TEST_SERVICE_PROXY3\"\n"
            + "SERVICE_PROXY_DENY != \"TEST_SERVICE_PROXY3\"\n").getBytes();

    private static final byte[] configDeniedData2 = ("PLACE_NAME = \"PlaceTest\"\n" + "SERVICE_NAME = \"TEST_SERVICE_NAME\"\n"
            + "SERVICE_TYPE = \"ANALYZE\"\n" + "SERVICE_DESCRIPTION = \"test place with denied list\"\n" + "SERVICE_COST = 60\n"
            + "SERVICE_QUALITY = 90\n" + "SERVICE_PROXY = \"TEST_SERVICE_PROXY\"\n"
            + "SERVICE_PROXY_DENY = \"TEST_SERVICE_PROXY\"\n" + "SERVICE_PROXY_DENY != \"*\"\n").getBytes();

    String CFGDIR = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);

    @Override
    @BeforeEach
    public void setUp() throws Exception {

        InputStream config = new ByteArrayInputStream(configData);
        place = new PlaceTest(config, null, "http://localhost:8001/PlaceTest");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
    }

    @Test
    void testConfiguration() {
        assertNotNull(place, "Place created and configured");
        assertEquals("PlaceTest", place.getPlaceName(), "Configured place name");
        assertEquals("TEST_SERVICE_PROXY", place.getPrimaryProxy(), "Primary proxy");
        assertEquals("TEST_SERVICE_PROXY.TEST_SERVICE_NAME.ANALYZE.http://localhost:8001/PlaceTest", place.getKey(), "Key generation");
        DirectoryEntry de = place.getDirectoryEntry();
        assertNotNull(de, "Directory entry");
        assertEquals(60, de.getCost(), "Cost in directory entry");
        assertEquals(90, de.getQuality(), "Quality in directory entry");
        assertEquals("test place", de.getDescription(), "Description in directory entry");

        place.addServiceProxy("FOO");
        place.addServiceProxy("BAR");
        Set<String> proxies = place.getProxies();
        assertNotNull(proxies, "Proxies as a set");
        assertEquals(3, proxies.size(), "Size of proxies set");
        assertTrue(proxies.contains("TEST_SERVICE_PROXY"), "Proxies does not contain original in set");
        assertTrue(proxies.contains("FOO"), "Proxies does not contains new in set");
        assertTrue(proxies.contains("BAR"), "Proxies does not contains last in set");
    }

    @Test
    void testNukem() {
        place.addServiceProxy("FOO-*");
        place.addServiceProxy("BAR(*)");

        IBaseDataObject d = new BaseDataObject();
        d.pushCurrentForm("TEST_SERVICE_PROXY");

        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(0, d.currentFormSize(), "Remove default form");

        d.pushCurrentForm("BAZ");
        d.pushCurrentForm("TEST_SERVICE_PROXY");
        d.pushCurrentForm("QUUZ");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(2, d.currentFormSize(), "Remove default form leaving others");

        d.replaceCurrentForm(null);

        d.pushCurrentForm("FOO-WILD");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(0, d.currentFormSize(), "Remove dash-wild form");

        d.pushCurrentForm("BAR(WILD)");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(0, d.currentFormSize(), "Remove paren-wild form");

        d.pushCurrentForm("FOO(WILD)");
        d.pushCurrentForm("BAR-WILD");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(2, d.currentFormSize(), "Do not remove on dash-paren cross wild");
        d.replaceCurrentForm(null);

        d.pushCurrentForm("FOO-BAR-BAZ");
        d.pushCurrentForm("FOO-BAR-BAR(WILD)");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(0, d.currentFormSize(), "Remove multi layered wild");

        d.pushCurrentForm("QUUZ(BANG)");
        d.pushCurrentForm("BANG(QUUZ)-FOO(BAR)");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(2, d.currentFormSize(), "Do not remove on dash-paren cross wild");
        d.pushCurrentForm("TEST_SERVICE_PROXY");
        place.addServiceProxy("*");
        ((PlaceTest) place).testNukeMyProxies(d);
        assertEquals(0, d.currentFormSize(), "Remove all on full wild");
    }

    @Test
    void testDuplicateProxy() {
        Set<String> proxies = place.getProxies();
        assertEquals(1, proxies.size(), "Place original proxy count ");
        place.addServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals(2, proxies.size(), "Place proxy count after addServiceProxy");
        place.addServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals(2, proxies.size(), "Place proxy count after duplicate addServcieProxy");

        place.removeServiceProxy("BAR");
        proxies = place.getProxies();
        assertEquals(2, proxies.size(), "Place proxy count after remove bogus entry");

        place.removeServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals(1, proxies.size(), "Place proxy count after removeServiceProxy");

        place.removeServiceProxy("TEST_SERVICE_PROXY");
        proxies = place.getProxies();
        assertNotNull(proxies, "No proxy place returns empty set not null");
        assertEquals(0, proxies.size(), "Place has all proxies removed!");

        place.addServiceProxy("FOO");
        proxies = place.getProxies();
        assertEquals(1, proxies.size(), "Place has 1 proxies after add onto an empty");
    }

    @Test
    void testLocOnlyConstructorWithResourceConfigData() {
        try {
            MyStreamConfigedTestPlace mtp = new MyStreamConfigedTestPlace("http://example.com:8001/MyStreamConfigedTestPlace");

            assertTrue(mtp.getFinishedSuperConstructor(), "MyStreamConfigedTestPlace did not initialize");

            mtp.shutDown();
        } catch (IOException iox) {
            fail("Could not initialize MyStreamConfigedTestPlace with one arg constructor", iox);
        }
    }

    @Test
    void testLocOnlyConstructorWithFileConfigDataAndNoPackage() throws IOException, EmissaryException {
        runFileConfiguredTest(false, 1);
    }

    @Test
    void testLocOnlyConstructorWithFileConfigDataAndPackage() throws IOException, EmissaryException {
        runFileConfiguredTest(true, 1);
    }

    private void runFileConfiguredTest(boolean usePackage, int ctorType) throws IOException, EmissaryException {
        Path cfg = null;
        OutputStream fos = null;
        try {
            // Write out the config data to the temp config dir
            if (usePackage) {
                cfg = Paths.get(CFGDIR, thisPackage.getName() + ".MyFileConfigedTestPlace" + ConfigUtil.CONFIG_FILE_ENDING);
            } else {
                cfg = Paths.get(CFGDIR, "MyFileConfigedTestPlace" + ConfigUtil.CONFIG_FILE_ENDING);
            }
            fos = Files.newOutputStream(cfg);
            fos.write(configData);

            MyFileConfigedTestPlace mtp = null;

            if (ctorType == 1) {
                // No config specified, auto-discovered
                mtp = new MyFileConfigedTestPlace("http://example.com:8001/MyFileConfigedTestPlace");
            } else if (ctorType == 2) {
                // String name of config file
                mtp = new MyFileConfigedTestPlace(cfg.getFileName().toString(), null, "http://example.com:8001/MyTestPlace");
            } else if (ctorType == 3) {
                // Stream input of config
                InputStream config = new ByteArrayInputStream(configData);
                mtp = new MyFileConfigedTestPlace(config, null, "http://example.com:8001/MyTestPlace");
            }

            assertNotNull(mtp, "Test run with bad ctypeType arg?");

            assertTrue(mtp.getFinishedSuperConstructor(), "MyFileConfigedTestPlace did not initialize");

            mtp.shutDown();
        } catch (IOException iox) {
            fail("Could not initialize MyFileConfigedTestPlace with one arg constructor", iox);
        } finally {
            // Clean up the tmp config settings
            restoreConfig();

            assert cfg != null;
            Files.deleteIfExists(cfg);

            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignore) {
                }
            }
            Files.deleteIfExists(cfg);
        }
    }

    @Test
    void testRequireOfCostParameter() {
        assertThrows(IOException.class, () -> {
            InputStream config = new ByteArrayInputStream(configDataMissingCost);
            new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
        });
    }

    @Test
    void testMBeanInteration() throws Exception {
        InputStream config = new ByteArrayInputStream(configDataWithResourceLimit);
        place = new PlaceTest(config, null, "http://example.com:8001/PlaceTest");

        if (place instanceof ServiceProviderPlaceMBean) {
            List<String> a = ((ServiceProviderPlaceMBean) place).getRunningConfig();
            assertTrue(a.size() > 0, "Running config must have some entries");
            String stats = ((ServiceProviderPlaceMBean) place).getPlaceStats();
            assertNotNull(stats, "Stats expected");
            long resourceLimit = ((ServiceProviderPlaceMBean) place).getResourceLimitMillis();
            assertEquals(10, resourceLimit, "Resource limit must be saved and returned");

            // These send output to the logger, just verify that they don't npe, they are
            // slightly useful to keep around for using in jconsole
            ((ServiceProviderPlaceMBean) place).dumpPlaceStats();
            ((ServiceProviderPlaceMBean) place).dumpRunningConfig();

        } else {
            fail("Place should be an instance of ServiceProviderPlaceMBean");
        }
    }


    @Test
    void testWithMissingPlaceLocation() throws Exception {
        InputStream config = new ByteArrayInputStream(configData);
        IServiceProviderPlace p = new PlaceTest(config, null, null);
        assertNotNull(p, "Place should be created with default location");
        assertTrue(p.getKey().contains("/PlaceTest"), "Place default location should be in key");

        config = new ByteArrayInputStream(configData);
        p = new PlaceTest(config, null);
        assertNotNull(p, "Place should be created with default location");
        assertTrue(p.getKey().contains("/PlaceTest"), "Place default location should be in key");

        config = new ByteArrayInputStream(configData);
        p = new PlaceTest(config);
        assertNotNull(p, "Place should be created with default location");
        assertTrue(p.getKey().contains("/PlaceTest"), "Place default location should be in key");

        config = new ByteArrayInputStream(configData);
        p = new PlaceTest(config);
        assertNotNull(p, "Place should be created with default location");
        assertTrue(p.getKey().contains("/PlaceTest"), "Place default location should be in key");
    }

    @Test
    void testRequireOfTypeParameter() {
        assertThrows(IOException.class, () -> {
            InputStream config = new ByteArrayInputStream(configDataMissingType);
            new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
        });
    }

    @Test
    void testRequireOfProxyOrKey() {
        assertThrows(IOException.class, () -> {
            InputStream config = new ByteArrayInputStream(configDataMissingProxy);
            new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
        });
    }

    @Test
    void testServiceKeyConfig() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
            assertEquals("TPlaceName", tp.getPlaceName(), "Configured place name");
            assertEquals("TPROXY", tp.getPrimaryProxy(), "Primary proxy");
            assertEquals("TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey(), "Key generation");
            DirectoryEntry de = tp.getDirectoryEntry();
            assertNotNull(de, "Directory entry");
            assertEquals(50, de.getCost(), "Cost in directory entry");
            assertEquals(50, de.getQuality(), "Quality in directory entry");
            assertEquals("test place", de.getDescription(), "Description in directory entry");

            Set<String> keys = tp.getKeys();
            assertNotNull(keys, "Keys as a set");
            assertEquals(1, keys.size(), "Size of Keys");

            Set<String> proxies = tp.getProxies();
            assertNotNull(proxies, "Proxies as a set");
            assertEquals(1, proxies.size(), "Size of proxies set");
            assertTrue(proxies.contains("TPROXY"), "Proxies contains original in set");
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
        }
    }

    @Test
    void testKeyConfigAddKey() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config, null, "http://example.com:8001/PlaceTest");
            assertEquals("TPlaceName", tp.getPlaceName(), "Configured place name");

            tp.addKey("TPROXY2.TNAME.IO.http://localhost:8001/TPlaceName$6050");

            Set<String> keys = tp.getKeys();
            assertNotNull(keys, "Keys as a set");
            assertEquals(2, keys.size(), "Size of Keys");

            assertEquals("TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey(), "Key generation uses first on list");

            // Add a bad key
            tp.addKey("TPROXY3.TNAME.http://localhost:8001/TPlaceName$6050");
            keys = tp.getKeys();
            assertNotNull(keys, "Keys as a set after bad key add");
            assertEquals(2, keys.size(), "Size of Keys after bad key add");


            Set<String> proxies = tp.getProxies();
            assertNotNull(proxies, "Proxies as a set");
            assertEquals(2, proxies.size(), "Size of proxies set");
            assertTrue(proxies.contains("TPROXY"), "Proxies contains original in set");
            assertTrue(proxies.contains("TPROXY2"), "Proxies contains original in set");
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
        }
    }

    @Test
    void testKeyConfigPlaceNameCtor() {
        try {
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("TPlaceName", tp.getPlaceName(), "Configured place name");
            assertEquals("TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey(), "Key generation uses first on list");

            Set<String> keys = tp.getKeys();
            assertNotNull(keys, "Keys as a set");
            assertEquals(1, keys.size(), "Size of Keys");

        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
        }
    }

    @Test
    void testKeyConfigShortPlaceName() {
        try {
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config, null, "PlaceTest");
            assertEquals("TPlaceName", tp.getPlaceName(), "Configured place name");
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
        }
    }

    @Test
    void testNoKeyInConfig() {
        assertThrows(IOException.class, () -> {
            InputStream config = new ByteArrayInputStream(configNoKeysData);
            new PlaceTest(config);
        });
    }

    @Test
    void testBadKeyInConfig() {
        assertThrows(IOException.class, () -> {
            InputStream config = new ByteArrayInputStream(configBadKeyData);
            new PlaceTest(config);
        });
    }

    @Test
    void testNoArgCtor() throws EmissaryException {
        // Point config to this package to find default config
        setConfig(null, true);
        try {
            PlaceTest tp = new PlaceTest();
            assertEquals("TestFooPlace", tp.getPlaceName(), "Configured place name");
        } catch (IOException iox) {
            fail("Place should have configured with no arg ctor", iox);
        } finally {
            restoreConfig();
        }
    }

    @Test
    void testKeysConfig() {
        try {
            // SERVICE_KEY = "TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050"
            // SERVICE_KEY = "TP2.TNAME.TRANSFORM.http://@{TGT_HOST}:@{TGT_PORT}/TP2PlaceName$6050"
            // SERVICE_KEY = "TP3.TNAME.ANALYZE.http://@{TGT_HOST}:@{TGT_PORT}/TP3PlaceName$7050"
            // SERVICE_KEY = "TP4.TNAME.IO.http://@{TGT_HOST}:@{TGT_PORT}/TP4PlaceName$8050"
            InputStream config = new ByteArrayInputStream(configKeysData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("TPlaceName", tp.getPlaceName(), "Configured place name");
            assertEquals("TPROXY", tp.getPrimaryProxy(), "Primary proxy");
            assertEquals("TPROXY.TNAME.ID.http://localhost:8001/TPlaceName", tp.getKey(), "Key generation uses first key");

            // Check directory entries
            DirectoryEntry de = tp.getDirectoryEntry();
            assertNotNull(de, "Directory entry");
            assertEquals(50, de.getCost(), "Cost in directory entry");
            assertEquals(50, de.getQuality(), "Quality in directory entry");
            assertEquals("test place", de.getDescription(), "Description in directory entry");

            // Check keys
            Set<String> keys = tp.getKeys();
            assertNotNull(keys, "Keys as a set");
            assertEquals(4, keys.size(), "Size of Keys");

            // Check namespace
            for (String k : keys) {
                String sl = KeyManipulator.getServiceLocation(k);
                try {
                    Object place = Namespace.lookup(sl);
                    assertNotNull(place, "Place must be found by service location");
                    assertSame(place, tp, "Place bound by service location must be correct object");
                } catch (Exception ex) {
                    fail("Should have found " + sl + " in namespace", ex);
                }
            }

            // Check proxies
            Set<String> proxies = tp.getProxies();
            assertNotNull(proxies, "Proxies as a set");
            assertEquals(4, proxies.size(), "Size of proxies set");
            assertTrue(proxies.contains("TPROXY"), "Proxies does not contain original in set");
            assertTrue(proxies.contains("TP2"), "Proxies does not contain original in set");
            assertTrue(proxies.contains("TP3"), "Proxies does not contain original in set");
            assertTrue(proxies.contains("TP4"), "Proxies does not contain original in set");
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
        }
    }

    @Test
    void testKeyRemoval() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("TPlaceName", tp.getPlaceName(), "Configured place name");
            tp.removeKey("TPROXY.TNAME.ID.http://localhost:8001/TPlaceName$5050");
            assertEquals("", tp.getPrimaryProxy(), "Primary proxy empty after key removal");
            assertEquals(0, tp.getKeys().size(), "Key size after removal must be 0");
            assertEquals(0, tp.getProxies().size(), "Proxy size after removal must be 0");
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
        }
    }

    @Test
    void testBogusKeyRemoval() {
        try {
            // TPROXY.TNAME.ID.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050
            InputStream config = new ByteArrayInputStream(configKeyData);
            PlaceTest tp = new PlaceTest(config);
            assertEquals("TPlaceName", tp.getPlaceName(), "Configured place name");
            tp.removeKey("TPROXY.TNAME.ANALYZE.http://localhost:8001/TPlaceName$5050");
            assertEquals("TPROXY", tp.getPrimaryProxy(), "Primary proxy present after bogus key removal");
            assertEquals(1, tp.getKeys().size(), "Key size after bogus removal must be 1");
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
        }
    }

    @Test
    void testDeniedServiceProxy() {
        try {
            InputStream config = new ByteArrayInputStream(configDeniedData);
            IServiceProviderPlace p = new PlaceTest(config);
            assertEquals("PlaceTest", p.getPlaceName(), "Configured place name");
            assertTrue(p.isDenied("TEST_SERVICE_PROXY"), "TEST_SERVICE_PROXY should be denied");
            assertTrue(!p.isDenied("TEST_SERVICE_PROXY2"), "TEST_SERVICE_PROXY2 should be allowed");
            assertTrue(!p.isDenied("TEST_SERVICE_PROXY3"), "TEST_SERVICE_PROXY3 should be allowed");

            InputStream config2 = new ByteArrayInputStream(configDeniedData2);
            IServiceProviderPlace p2 = new PlaceTest(config2);
            assertTrue(!p2.isDenied("TEST_SERVICE_PROXY"), "TEST_SERVICE_PROXY should be allowed");
            assertTrue(!p2.isDenied("TEST_SERVICE_PROXY2"), "TEST_SERVICE_PROXY2 should be allowed");
        } catch (IOException iox) {
            fail("Place should have configured with SERVICE_KEY", iox);
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
