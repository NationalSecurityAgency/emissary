package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import emissary.util.xml.JDOMUtil;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DirectoryEntryTest extends UnitTest {

    private static String key = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/ThePlace";
    private static int cost = 50;
    private static int quality = 50;

    private DirectoryEntry d = null;
    private DirectoryEntry d2 = null;

    @Override
    @Before
    public void setUp() throws Exception {
        this.d = new DirectoryEntry(key, "This is a place", cost, quality);
        this.d2 = new DirectoryEntry(key, "Another place", cost * 2, quality);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.d = null;
        this.d2 = null;
    }

    @Test
    public void testConstructor() {
        assertEquals("Cost set via ctor", cost, this.d.getCost());
        assertEquals("Quality set via ctor", quality, this.d.getQuality());
        assertEquals("Key set via ctor", key, this.d.getKey());
        assertEquals("Description via ctor", "This is a place", this.d.getDescription());
    }

    @Test
    public void testParsing() {
        assertEquals("Service type", "ID", this.d.getServiceType());
        assertEquals("Service name", "FOOPLACE", this.d.getServiceName());
        assertEquals("Calculate expense", 5050, this.d.getExpense());
        assertEquals("DataId", "UNKNOWN::ID", this.d.getDataID());
        assertEquals("Data Type", emissary.core.Form.UNKNOWN, this.d.getDataType());
        assertEquals("ServiceHostURL", "http://host.domain.com:8001/", this.d.getServiceHostURL());
        assertNull("Local place", this.d.getLocalPlace());
    }

    @Test
    public void testReverseCostComp() {
        final DirectoryEntry n = new DirectoryEntry(key);
        assertEquals("Computed cost", 0, n.getCost());
        assertEquals("Computed quality", 100, n.getQuality());
        assertEquals("Computed expense", 0, n.getExpense());

        final DirectoryEntry n2 = new DirectoryEntry(key + "$5050");
        assertEquals("Computed cost", cost, n2.getCost());
        assertEquals("Computed quality", quality, n2.getQuality());
        assertEquals("Computed expense", 5050, n2.getExpense());
    }

    @Test
    public void testBetter() {
        assertTrue("External expense comparison", this.d.getExpense() < this.d2.getExpense());
        assertTrue("Cheaper is better", this.d.isBetterThan(this.d2));
        assertFalse("More expensive is not better", this.d2.isBetterThan(this.d));
    }

    @Test
    public void testAdd() {
        this.d.addCost(100);
        assertEquals("Cost increment", 100 + cost, this.d.getCost());
        final int newExp = DirectoryEntry.calculateExpense(cost + 100, quality);
        assertEquals("Expense computation on cost increment", newExp, this.d.getExpense());
        assertTrue("Correct expense in key", this.d.toString().indexOf("$" + newExp) > -1);

        final int origWeight = this.d.getPathWeight();
        this.d.addPathWeight(100);
        assertEquals("Path weight incrememented", 100 + origWeight, this.d.getPathWeight());

        this.d.addPathWeight(-100000);
        assertEquals("Path weight cannot be negative", 0, this.d.getPathWeight());

        this.d.setPathWeight(-1);
        assertEquals("Path weight cannot be negative", 0, this.d.getPathWeight());
    }

    @Test
    public void testXmlToObject() throws JDOMException {
        final int exp = DirectoryEntry.calculateExpense(cost, quality);
        final String xml =
                "<entry><key>" + key + "</key><cost>" + cost + "</cost><quality>" + quality
                        + "</quality><description>This is the description</description><expense>" + exp + "</expense><place>" + key
                        + "</place></entry>";
        final Document jdom = JDOMUtil.createDocument(xml, false);
        final DirectoryEntry dxml = DirectoryEntry.fromXML(jdom.getRootElement());
        assertNotNull("DirectoryEntry created from xml", dxml);
        assertEquals("Cost from xml", cost, dxml.getCost());
        assertEquals("Quality from xml", quality, dxml.getQuality());
        assertEquals("Expense from xml", exp, dxml.getExpense());
        assertEquals("Description from xml", "This is the description", dxml.getDescription());
        assertEquals("Key from xml", key, dxml.getKey());
        assertEquals("Full key from xml", key + KeyManipulator.DOLLAR + exp, dxml.getFullKey());
    }

    @Test
    public void testEquality() {
        assertFalse("Entry equality", this.d.equals(KeyManipulator.addExpense(this.d2.getKey(), this.d2.getExpense())));
        assertFalse("Entry equality", this.d2.equals(KeyManipulator.addExpense(this.d.getKey(), this.d.getExpense())));
        assertTrue("Entry equality without cost", this.d.equalsIgnoreCost(KeyManipulator.addExpense(this.d2.getKey(), this.d2.getExpense())));
        assertTrue("Entry equality without cost", this.d2.equalsIgnoreCost(KeyManipulator.addExpense(this.d.getKey(), this.d.getExpense())));
    }

    @Test
    public void testProxySettings() {
        String proxyKey = "*.*.*.http://host2.domain.com:9001/OtherPlace";
        this.d.proxyFor(proxyKey);
        assertEquals("Proxy cost", cost, this.d.getCost());
        assertEquals("Proxy qual", quality, this.d.getQuality());
        assertEquals("Proxy service type", KeyManipulator.getServiceType(key), this.d.getServiceType());
        assertEquals("Proxy service name", KeyManipulator.getServiceName(key), this.d.getServiceName());
        assertEquals("Proxy data type", KeyManipulator.getDataType(key), KeyManipulator.getDataType(this.d.getKey()));
        assertEquals("Proxy expense", DirectoryEntry.calculateExpense(cost, quality), this.d.getExpense());

        // Proxy key cost does not change it
        proxyKey = "*.*.*.http://host2.domain.com:9001/OtherPlace$12345";
        this.d.proxyFor(proxyKey);
        assertEquals("Proxy cost", cost, this.d.getCost());
        assertEquals("Proxy qual", quality, this.d.getQuality());
        assertEquals("Proxy service type", KeyManipulator.getServiceType(key), this.d.getServiceType());
        assertEquals("Proxy service name", KeyManipulator.getServiceName(key), this.d.getServiceName());
        assertEquals("Proxy data type", KeyManipulator.getDataType(key), KeyManipulator.getDataType(this.d.getKey()));
        assertEquals("Proxy expense", DirectoryEntry.calculateExpense(cost, quality), this.d.getExpense());
    }

    @Test
    public void testCopyConstructor() {
        final DirectoryEntry copy = new DirectoryEntry(this.d);
        assertEquals("Copied key", key, copy.getKey());
        assertEquals("Copied cost", cost, copy.getCost());
        assertEquals("Copied qual", quality, copy.getQuality());
        assertEquals("Copied expense", this.d.getExpense(), copy.getExpense());
        assertEquals("Copied desc", this.d.getDescription(), copy.getDescription());
        assertEquals("Copied tostring", this.d.toString(), copy.toString());
        assertEquals("Full key", this.d.getFullKey(), copy.getFullKey());
        assertEquals("Copied path weight", this.d.getPathWeight(), copy.getPathWeight());
    }

    @Test
    public void testUpdateMethods() {
        this.d.setDataType("KNOWN");
        assertEquals("update key with new data type", "KNOWN", KeyManipulator.getDataType(this.d.getFullKey()));
        assertEquals("update dataid with new data type", "KNOWN::ID", this.d.getDataID());

        this.d.setServiceType("DI");
        assertEquals("update key with new service type", "DI", KeyManipulator.getServiceType(this.d.getFullKey()));
        assertEquals("update dataid with new service type", "KNOWN::DI", this.d.getDataID());

        this.d.setServiceName("BARPLACE");
        assertEquals("update key with new service name", "BARPLACE", KeyManipulator.getServiceName(this.d.getFullKey()));
        assertEquals("no change to dataid with new service name", "KNOWN::DI", this.d.getDataID());
    }

    @Test
    public void testAge() {
        final DirectoryEntry x1 = new DirectoryEntry(key, "Another place", cost * 2, quality);
        pause(50);
        final DirectoryEntry x2 = new DirectoryEntry(key, "Another fine place", cost * 2, quality);
        assertTrue("Age must vary by creation time", x1.getAge() < x2.getAge());
    }

    @Test
    public void testAgeOnCopy() {
        final DirectoryEntry x1 = new DirectoryEntry(key, "Another place", cost * 2, quality);
        pause(50);
        final DirectoryEntry x2 = new DirectoryEntry(x1);
        assertTrue("Age must vary by creation time on a copy", x1.getAge() < x2.getAge());
    }
}
