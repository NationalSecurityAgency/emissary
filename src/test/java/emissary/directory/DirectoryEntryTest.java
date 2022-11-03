package emissary.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emissary.core.Form;
import emissary.test.core.junit5.UnitTest;
import emissary.util.xml.JDOMUtil;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectoryEntryTest extends UnitTest {

    private static final String key = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/ThePlace";
    private static final int cost = 50;
    private static final int quality = 50;

    private DirectoryEntry d = null;
    private DirectoryEntry d2 = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.d = new DirectoryEntry(key, "This is a place", cost, quality);
        this.d2 = new DirectoryEntry(key, "Another place", cost * 2, quality);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        this.d = null;
        this.d2 = null;
    }

    @Test
    void testConstructor() {
        assertEquals(cost, this.d.getCost(), "Cost set via ctor");
        assertEquals(quality, this.d.getQuality(), "Quality set via ctor");
        assertEquals(key, this.d.getKey(), "Key set via ctor");
        assertEquals("This is a place", this.d.getDescription(), "Description via ctor");
    }

    @Test
    void testParsing() {
        assertEquals("ID", this.d.getServiceType(), "Service type");
        assertEquals("FOOPLACE", this.d.getServiceName(), "Service name");
        assertEquals(5050, this.d.getExpense(), "Calculate expense");
        assertEquals("UNKNOWN::ID", this.d.getDataID(), "DataId");
        assertEquals(Form.UNKNOWN, this.d.getDataType(), "Data Type");
        assertEquals("http://host.domain.com:8001/", this.d.getServiceHostURL(), "ServiceHostURL");
        assertNull(this.d.getLocalPlace(), "Local place");
    }

    @Test
    void testReverseCostComp() {
        final DirectoryEntry n = new DirectoryEntry(key);
        assertEquals(0, n.getCost(), "Computed cost");
        assertEquals(100, n.getQuality(), "Computed quality");
        assertEquals(0, n.getExpense(), "Computed expense");

        final DirectoryEntry n2 = new DirectoryEntry(key + "$5050");
        assertEquals(cost, n2.getCost(), "Computed cost");
        assertEquals(quality, n2.getQuality(), "Computed quality");
        assertEquals(5050, n2.getExpense(), "Computed expense");
    }

    @Test
    void testBetter() {
        assertTrue(this.d.getExpense() < this.d2.getExpense(), "External expense comparison");
        assertTrue(this.d.isBetterThan(this.d2), "Cheaper is better");
        assertFalse(this.d2.isBetterThan(this.d), "More expensive is not better");
    }

    @Test
    void testAdd() {
        this.d.addCost(100);
        assertEquals(100 + cost, this.d.getCost(), "Cost increment");
        final int newExp = DirectoryEntry.calculateExpense(cost + 100, quality);
        assertEquals(newExp, this.d.getExpense(), "Expense computation on cost increment");
        assertTrue(this.d.toString().contains("$" + newExp), "Correct expense in key");

        final int origWeight = this.d.getPathWeight();
        this.d.addPathWeight(100);
        assertEquals(100 + origWeight, this.d.getPathWeight(), "Path weight incrememented");

        this.d.addPathWeight(-100000);
        assertEquals(0, this.d.getPathWeight(), "Path weight cannot be negative");

        this.d.setPathWeight(-1);
        assertEquals(0, this.d.getPathWeight(), "Path weight cannot be negative");
    }

    @Test
    void testXmlToObject() throws JDOMException {
        final int exp = DirectoryEntry.calculateExpense(cost, quality);
        final String xml =
                "<entry><key>" + key + "</key><cost>" + cost + "</cost><quality>" + quality
                        + "</quality><description>This is the description</description><expense>" + exp + "</expense><place>" + key
                        + "</place></entry>";
        final Document jdom = JDOMUtil.createDocument(xml, false);
        final DirectoryEntry dxml = DirectoryEntry.fromXML(jdom.getRootElement());
        assertNotNull(dxml, "DirectoryEntry created from xml");
        assertEquals(cost, dxml.getCost(), "Cost from xml");
        assertEquals(quality, dxml.getQuality(), "Quality from xml");
        assertEquals(exp, dxml.getExpense(), "Expense from xml");
        assertEquals("This is the description", dxml.getDescription(), "Description from xml");
        assertEquals(key, dxml.getKey(), "Key from xml");
        assertEquals(key + KeyManipulator.DOLLAR + exp, dxml.getFullKey(), "Full key from xml");
    }

    @Test
    void testEquality() {
        assertFalse(this.d.equals(KeyManipulator.addExpense(this.d2.getKey(), this.d2.getExpense())), "Entry equality");
        assertFalse(this.d2.equals(KeyManipulator.addExpense(this.d.getKey(), this.d.getExpense())), "Entry equality");
        assertTrue(this.d.equalsIgnoreCost(KeyManipulator.addExpense(this.d2.getKey(), this.d2.getExpense())), "Entry equality without cost");
        assertTrue(this.d2.equalsIgnoreCost(KeyManipulator.addExpense(this.d.getKey(), this.d.getExpense())), "Entry equality without cost");
    }

    @Test
    void testProxySettings() {
        String proxyKey = "*.*.*.http://host2.domain.com:9001/OtherPlace";
        this.d.proxyFor(proxyKey);
        assertEquals(cost, this.d.getCost(), "Proxy cost");
        assertEquals(quality, this.d.getQuality(), "Proxy qual");
        assertEquals(KeyManipulator.getServiceType(key), this.d.getServiceType(), "Proxy service type");
        assertEquals(KeyManipulator.getServiceName(key), this.d.getServiceName(), "Proxy service name");
        assertEquals(KeyManipulator.getDataType(key), KeyManipulator.getDataType(this.d.getKey()), "Proxy data type");
        assertEquals(DirectoryEntry.calculateExpense(cost, quality), this.d.getExpense(), "Proxy expense");

        // Proxy key cost does not change it
        proxyKey = "*.*.*.http://host2.domain.com:9001/OtherPlace$12345";
        this.d.proxyFor(proxyKey);
        assertEquals(cost, this.d.getCost(), "Proxy cost");
        assertEquals(quality, this.d.getQuality(), "Proxy qual");
        assertEquals(KeyManipulator.getServiceType(key), this.d.getServiceType(), "Proxy service type");
        assertEquals(KeyManipulator.getServiceName(key), this.d.getServiceName(), "Proxy service name");
        assertEquals(KeyManipulator.getDataType(key), KeyManipulator.getDataType(this.d.getKey()), "Proxy data type");
        assertEquals(DirectoryEntry.calculateExpense(cost, quality), this.d.getExpense(), "Proxy expense");
    }

    @Test
    void testCopyConstructor() {
        final DirectoryEntry copy = new DirectoryEntry(this.d);
        assertEquals(key, copy.getKey(), "Copied key");
        assertEquals(cost, copy.getCost(), "Copied cost");
        assertEquals(quality, copy.getQuality(), "Copied qual");
        assertEquals(this.d.getExpense(), copy.getExpense(), "Copied expense");
        assertEquals(this.d.getDescription(), copy.getDescription(), "Copied desc");
        assertEquals(this.d.toString(), copy.toString(), "Copied tostring");
        assertEquals(this.d.getFullKey(), copy.getFullKey(), "Full key");
        assertEquals(this.d.getPathWeight(), copy.getPathWeight(), "Copied path weight");
    }

    @Test
    void testUpdateMethods() {
        this.d.setDataType("KNOWN");
        assertEquals("KNOWN", KeyManipulator.getDataType(this.d.getFullKey()), "update key with new data type");
        assertEquals("KNOWN::ID", this.d.getDataID(), "update dataid with new data type");

        this.d.setServiceType("DI");
        assertEquals("DI", KeyManipulator.getServiceType(this.d.getFullKey()), "update key with new service type");
        assertEquals("KNOWN::DI", this.d.getDataID(), "update dataid with new service type");

        this.d.setServiceName("BARPLACE");
        assertEquals("BARPLACE", KeyManipulator.getServiceName(this.d.getFullKey()), "update key with new service name");
        assertEquals("KNOWN::DI", this.d.getDataID(), "no change to dataid with new service name");
    }

    @Test
    void testAge() {
        final DirectoryEntry x1 = new DirectoryEntry(key, "Another place", cost * 2, quality);
        pause(50);
        final DirectoryEntry x2 = new DirectoryEntry(key, "Another fine place", cost * 2, quality);
        assertTrue(x1.getAge() < x2.getAge(), "Age must vary by creation time");
    }

    @Test
    void testAgeOnCopy() {
        final DirectoryEntry x1 = new DirectoryEntry(key, "Another place", cost * 2, quality);
        pause(50);
        final DirectoryEntry x2 = new DirectoryEntry(x1);
        assertTrue(x1.getAge() < x2.getAge(), "Age must vary by creation time on a copy");
    }
}
