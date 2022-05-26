package emissary.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class KeyManipulatorTest extends UnitTest {

    @Test
    void testParsing() {
        final String t = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/thePlace$5050";

        assertEquals(emissary.core.Form.UNKNOWN, KeyManipulator.getDataType(t), "Data type");
        assertEquals("FOOPLACE", KeyManipulator.getServiceName(t), "Service name");
        assertEquals("ID", KeyManipulator.getServiceType(t), "Service type");
        assertEquals("host.domain.com:8001", KeyManipulator.getServiceHost(t), "Service host");
        assertEquals("http://host.domain.com:8001/thePlace", KeyManipulator.getServiceLocation(t), "Service location");
        assertEquals(5050, KeyManipulator.getExpense(t), "Expense");
        assertEquals("thePlace", KeyManipulator.getServiceClassname(t), "Classname");
        assertEquals("UNKNOWN::ID", KeyManipulator.getDataID(t), "Data ID");
        assertTrue(KeyManipulator.isKeyComplete(t), "Key is complete");
    }

    @Test
    void testCost() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String t = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";

        assertEquals(5050, KeyManipulator.getExpense(t, -99), "Expense");
        assertEquals(-99, KeyManipulator.getExpense(s, -99), "Default expense");

        assertEquals(5150, KeyManipulator.getExpense(KeyManipulator.addExpense(t, 5150)), "Expense addition");
        assertEquals(5050, KeyManipulator.getExpense(KeyManipulator.addExpense(t, 5050)), "Expense no change during addition");
        assertEquals(100, KeyManipulator.getExpense(KeyManipulator.addExpense(s, 100)), "Expense addition to default");


        assertEquals(5050 + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD,
                KeyManipulator.getExpense(KeyManipulator.addRemoteCostIfMoved(s, t)),
                "Remote expense addition");
        assertEquals(5050, KeyManipulator.getExpense(KeyManipulator.addRemoteCostIfMoved(t, t)), "Remote expense no addition");
    }

    @Test
    void testRemoveExpense() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String t = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";
        final String sx = KeyManipulator.removeExpense(s);
        final String tx = KeyManipulator.removeExpense(t);
        assertEquals(s, sx, "No change on remove expense");
        assertEquals("UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace", tx, "Removed expense");
    }

    @Test
    void testBogusKeys() {
        assertEquals("::", KeyManipulator.getDataID("THISISATEST"), "Bogus data id");
        assertFalse(KeyManipulator.isKeyComplete("THISISATEST"), "Incomplete key");
        assertFalse(KeyManipulator.isKeyComplete("THISIS.ATEST"), "Incomplete key");
        assertFalse(KeyManipulator.isKeyComplete("THIS.IS.ATEST"), "Incomplete key");
        assertFalse(KeyManipulator.isKeyComplete("THIS.IS.ATEST.http://"), "Incomplete key");
        assertFalse(KeyManipulator.isKeyComplete("THIS.IS.ATEST.http://"), "Incomplete key");
    }


    @Test
    void testSproutKey() {
        assertTrue(KeyManipulator.makeSproutKey("http://thehost.domain.dom:888/thePlace").contains("<SPROUT>"), "Creating sprout key");
    }

    @Test
    void testMatch() {
        final String t = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";
        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.*"), "Gmatch on data type");
        assertTrue(KeyManipulator.gmatch(t, "*.FOOPLACE.*"), "Gmatch on serivce type");
        assertTrue(KeyManipulator.gmatch(t, "*.ID.*"), "Gmatch on data type");

        assertTrue(KeyManipulator.gmatch(t, "*.*.*.*"), "Gmatch on four wilds");
        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.*.*.*"), "Gmatch on three wilds");
        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.FOOPLACE.*.*"), "Gmatch on two wilds");
        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.FOOPLACE.ID.*"), "Gmatch on one wild");

        assertFalse(KeyManipulator.gmatch(t, "*.*.*.*x"), "Gmatch on four wilds");
        assertFalse(KeyManipulator.gmatch(t, "A.*.*.*"), "Gmatch on wilds wrong first");
        assertFalse(KeyManipulator.gmatch(t, "*.B.*.*"), "Gmatch on wilds wrong second");
        assertFalse(KeyManipulator.gmatch(t, "*.*.DORK.*"), "Gmatch on wilds wrong third");
        assertFalse(KeyManipulator.gmatch(t, "*.*.*.tcp://hostb.domain.com:8001/thePlace$5050"), "Gmatch on wilds wrong fourth");

        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.?OOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050"), "Gmatch on single pos wildcard");
        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.???PLACE.ID.http://hostb.domain.com:8001/thePlace$5050"), "Gmatch on triple pos wildcard");
        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.?OOPLACE.?D.http://hostb.domain.com:?001/thePlace$5050"), "Gmatch on disjoint ? wildcard");
        assertFalse(KeyManipulator.gmatch(t, "UNKNOWN.?FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050"),
                "No Gmatch on wrong single pos wildcard");
        assertFalse(KeyManipulator.gmatch(t, "BLUBBER.*"), "No Gmatch on wrong data type");
        assertTrue(KeyManipulator.gmatch(t, "UNKNOWN.*.ID.*"), "Gmatch on data id");

    }

    @Test
    void testIsLocalTo() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String f1 = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";
        final String f2 = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:9999/thePlace$5050";
        final String f3 = "UNKNOWN.FOOPLACE.ID.tcp://hostb.domain.com:8001/thePlace$5050";
        final String t1 = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/theOtherPlace$5050";
        final String t2 = "BAR.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace$5050";

        assertFalse(KeyManipulator.isLocalTo(s, f1), "Differing hosts");
        assertFalse(KeyManipulator.isLocalTo(s, f2), "Differing ports");
        assertFalse(KeyManipulator.isLocalTo(s, f3), "Differing protocols");
        assertTrue(KeyManipulator.isLocalTo(s, t1), "Differing classes");
        assertTrue(KeyManipulator.isLocalTo(s, t2), "Different proxies");
    }

    @Test
    void testHostKey() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String wc = KeyManipulator.getDefaultDirectoryKey(s);
        final String hc = KeyManipulator.getHostMatchKey(s);
        assertEquals("*.*.*.http://hosta.domain.com:8001/DirectoryPlace", wc, "Default directory key");
        assertEquals("*.*.*.http://hosta.domain.com:8001/*", hc, "Host match key");
        assertTrue(KeyManipulator.gmatch(s, hc), "Default directory matches self");
        assertTrue(KeyManipulator.gmatch(wc, hc), "Host match matches default directory");
    }

    /**
     * Validates that getDefaultDirectoryKey ignores arbitrary characters before the first separator
     */
    @Test
    void testGetDefaultDirectoryKeyIgnoresNonSeparatorPrefix() {

        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String prefix = "#";

        String expected = KeyManipulator.getDefaultDirectoryKey(s);
        String actual = KeyManipulator.getDefaultDirectoryKey(prefix + s);
        assertEquals(expected, actual, "Non-separator prefix should be ignored");
    }

    @Test
    void testServiceTypeExtraction() {
        final String dataId = "FOO::BAR";
        assertEquals("BAR", KeyManipulator.getServiceTypeFromDataID(dataId), "Service type from dataID");
        assertEquals("", KeyManipulator.getServiceTypeFromDataID("FOOBAR"), "Service type from bogus dataID");
    }

    @Test
    void testProxyKeyGen() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace$9090";
        final String proxyHost = "*.*.*.http://hostb.otherdomain.com:9001/OtherPlace";
        assertEquals("UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$9090",
                KeyManipulator.makeProxyKey(s, proxyHost, 9090),
                "Proxy key construction");
        assertEquals("UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$9090",
                KeyManipulator.makeProxyKey(s, proxyHost, 8080),
                "Proxy key construction");

        final String t = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        assertEquals("UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$9090",
                KeyManipulator.makeProxyKey(t, proxyHost, 9090),
                "Proxy key construction");
        assertEquals("UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$8080",
                KeyManipulator.makeProxyKey(t, proxyHost, 8080),
                "Proxy key construction");
        assertEquals("UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace",
                KeyManipulator.makeProxyKey(t, proxyHost, -1),
                "Proxy key dflt exp construction");

        final String u = "UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace";
        assertEquals(u, KeyManipulator.makeProxyKey(u, proxyHost, -1), "Proxy key is local already");
        assertEquals(u + "$9090", KeyManipulator.makeProxyKey(u, proxyHost, 9090), "Proxy key is local already but needs cost");
    }
}
