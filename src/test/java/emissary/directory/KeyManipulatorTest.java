package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class KeyManipulatorTest extends UnitTest {

    @Test
    public void testParsing() {
        final String t = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/thePlace$5050";

        assertEquals("Data type", emissary.core.Form.UNKNOWN, KeyManipulator.getDataType(t));
        assertEquals("Service name", "FOOPLACE", KeyManipulator.getServiceName(t));
        assertEquals("Service type", "ID", KeyManipulator.getServiceType(t));
        assertEquals("Service host", "host.domain.com:8001", KeyManipulator.getServiceHost(t));
        assertEquals("Service location", "http://host.domain.com:8001/thePlace", KeyManipulator.getServiceLocation(t));
        assertEquals("Expense", 5050, KeyManipulator.getExpense(t));
        assertEquals("Classname", "thePlace", KeyManipulator.getServiceClassname(t));
        assertEquals("Data ID", "UNKNOWN::ID", KeyManipulator.getDataID(t));
        assertTrue("Key is complete", KeyManipulator.isKeyComplete(t));
    }

    @Test
    public void testCost() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String t = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";

        assertEquals("Expense", 5050, KeyManipulator.getExpense(t, -99));
        assertEquals("Default expense", -99, KeyManipulator.getExpense(s, -99));

        assertEquals("Expense addition", 5150, KeyManipulator.getExpense(KeyManipulator.addExpense(t, 5150)));
        assertEquals("Expense no change during addition", 5050, KeyManipulator.getExpense(KeyManipulator.addExpense(t, 5050)));
        assertEquals("Expense addition to default", 100, KeyManipulator.getExpense(KeyManipulator.addExpense(s, 100)));


        assertEquals("Remote expense addition", 5050 + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD,
                KeyManipulator.getExpense(KeyManipulator.addRemoteCostIfMoved(s, t)));
        assertEquals("Remote expense no addition", 5050, KeyManipulator.getExpense(KeyManipulator.addRemoteCostIfMoved(t, t)));
    }

    @Test
    public void testRemoveExpense() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String t = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";
        final String sx = KeyManipulator.removeExpense(s);
        final String tx = KeyManipulator.removeExpense(t);
        assertEquals("No change on remove expense", s, sx);
        assertEquals("Removed expense", "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace", tx);
    }

    @Test
    public void testBogusKeys() {
        assertEquals("Bogus data id", "::", KeyManipulator.getDataID("THISISATEST"));
        assertFalse("Incomplete key", KeyManipulator.isKeyComplete("THISISATEST"));
        assertFalse("Incomplete key", KeyManipulator.isKeyComplete("THISIS.ATEST"));
        assertFalse("Incomplete key", KeyManipulator.isKeyComplete("THIS.IS.ATEST"));
        assertFalse("Incomplete key", KeyManipulator.isKeyComplete("THIS.IS.ATEST.http://"));
        assertFalse("Incomplete key", KeyManipulator.isKeyComplete("THIS.IS.ATEST.http://"));
    }


    @Test
    public void testSproutKey() {
        assertTrue("Creating sprout key", KeyManipulator.makeSproutKey("http://thehost.domain.dom:888/thePlace").indexOf("<SPROUT>") > -1);
    }

    @Test
    public void testMatch() {
        final String t = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";
        assertTrue("Gmatch on data type", KeyManipulator.gmatch(t, "UNKNOWN.*"));
        assertTrue("Gmatch on serivce type", KeyManipulator.gmatch(t, "*.FOOPLACE.*"));
        assertTrue("Gmatch on data type", KeyManipulator.gmatch(t, "*.ID.*"));

        assertTrue("Gmatch on four wilds", KeyManipulator.gmatch(t, "*.*.*.*"));
        assertTrue("Gmatch on three wilds", KeyManipulator.gmatch(t, "UNKNOWN.*.*.*"));
        assertTrue("Gmatch on two wilds", KeyManipulator.gmatch(t, "UNKNOWN.FOOPLACE.*.*"));
        assertTrue("Gmatch on one wild", KeyManipulator.gmatch(t, "UNKNOWN.FOOPLACE.ID.*"));

        assertFalse("Gmatch on four wilds", KeyManipulator.gmatch(t, "*.*.*.*x"));
        assertFalse("Gmatch on wilds wrong first", KeyManipulator.gmatch(t, "A.*.*.*"));
        assertFalse("Gmatch on wilds wrong second", KeyManipulator.gmatch(t, "*.B.*.*"));
        assertFalse("Gmatch on wilds wrong third", KeyManipulator.gmatch(t, "*.*.DORK.*"));
        assertFalse("Gmatch on wilds wrong fourth", KeyManipulator.gmatch(t, "*.*.*.tcp://hostb.domain.com:8001/thePlace$5050"));

        assertTrue("Gmatch on single pos wildcard", KeyManipulator.gmatch(t, "UNKNOWN.?OOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050"));
        assertTrue("Gmatch on triple pos wildcard", KeyManipulator.gmatch(t, "UNKNOWN.???PLACE.ID.http://hostb.domain.com:8001/thePlace$5050"));
        assertTrue("Gmatch on disjoint ? wildcard", KeyManipulator.gmatch(t, "UNKNOWN.?OOPLACE.?D.http://hostb.domain.com:?001/thePlace$5050"));
        assertFalse("No Gmatch on wrong single pos wildcard",
                KeyManipulator.gmatch(t, "UNKNOWN.?FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050"));
        assertFalse("No Gmatch on wrong data type", KeyManipulator.gmatch(t, "BLUBBER.*"));
        assertTrue("Gmatch on data id", KeyManipulator.gmatch(t, "UNKNOWN.*.ID.*"));

    }

    @Test
    public void testIsLocalTo() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String f1 = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:8001/thePlace$5050";
        final String f2 = "UNKNOWN.FOOPLACE.ID.http://hostb.domain.com:9999/thePlace$5050";
        final String f3 = "UNKNOWN.FOOPLACE.ID.tcp://hostb.domain.com:8001/thePlace$5050";
        final String t1 = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/theOtherPlace$5050";
        final String t2 = "BAR.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace$5050";

        assertFalse("Differing hosts", KeyManipulator.isLocalTo(s, f1));
        assertFalse("Differing ports", KeyManipulator.isLocalTo(s, f2));
        assertFalse("Differing protocols", KeyManipulator.isLocalTo(s, f3));
        assertTrue("Differing classes", KeyManipulator.isLocalTo(s, t1));
        assertTrue("Different proxies", KeyManipulator.isLocalTo(s, t2));
    }

    @Test
    public void testHostKey() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        final String wc = KeyManipulator.getDefaultDirectoryKey(s);
        final String hc = KeyManipulator.getHostMatchKey(s);
        assertEquals("Default directory key", "*.*.*.http://hosta.domain.com:8001/DirectoryPlace", wc);
        assertEquals("Host match key", "*.*.*.http://hosta.domain.com:8001/*", hc);
        assertTrue("Defualt directory matches self", KeyManipulator.gmatch(s, hc));
        assertTrue("Host match matches default directory", KeyManipulator.gmatch(wc, hc));
    }

    @Test
    public void testServiceTypeExtraction() {
        final String dataId = "FOO::BAR";
        assertEquals("Service type from dataID", "BAR", KeyManipulator.getServiceTypeFromDataID(dataId));
        assertEquals("Service type from bogus dataID", "", KeyManipulator.getServiceTypeFromDataID("FOOBAR"));
    }

    @Test
    public void testProxyKeyGen() {
        final String s = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace$9090";
        final String proxyHost = "*.*.*.http://hostb.otherdomain.com:9001/OtherPlace";
        assertEquals("Proxy key construction", "UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$9090",
                KeyManipulator.makeProxyKey(s, proxyHost, 9090));
        assertEquals("Proxy key construction", "UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$9090",
                KeyManipulator.makeProxyKey(s, proxyHost, 8080));

        final String t = "UNKNOWN.FOOPLACE.ID.http://hosta.domain.com:8001/thePlace";
        assertEquals("Proxy key construction", "UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$9090",
                KeyManipulator.makeProxyKey(t, proxyHost, 9090));
        assertEquals("Proxy key construction", "UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace$8080",
                KeyManipulator.makeProxyKey(t, proxyHost, 8080));
        assertEquals("Proxy key dflt exp construction", "UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace",
                KeyManipulator.makeProxyKey(t, proxyHost, -1));

        final String u = "UNKNOWN.FOOPLACE.ID.http://hostb.otherdomain.com:9001/OtherPlace";
        assertEquals("Proxy key is local already", u, KeyManipulator.makeProxyKey(u, proxyHost, -1));
        assertEquals("Proxy key is local already but needs cost", u + "$9090", KeyManipulator.makeProxyKey(u, proxyHost, 9090));
    }
}
