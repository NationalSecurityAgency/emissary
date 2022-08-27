package emissary.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import emissary.test.core.junit5.UnitTest;
import emissary.util.xml.JDOMUtil;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectoryEntryListTest extends UnitTest {

    private DirectoryEntryList dl = null;
    private static final String key1 = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/thePlace";
    private static final String key2 = "UNKNOWN.FOOPLACE.TRANSFORM.http://host.domain.com:8001/otherPlace";
    private static final String key3 = "UNKNOWN.BARPLACE.TRANSFORM.http://host.domain.com:8001/barPlace";
    private static final int cost = 50;
    private static final int quality = 50;

    private DirectoryEntry d = null;
    private DirectoryEntry d2 = null;
    private DirectoryEntry d3 = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.dl = new DirectoryEntryList();
        this.d = new DirectoryEntry(key1, "This is a place", cost, quality);
        this.d2 = new DirectoryEntry(key2, "Another place", cost * 2, quality);
        this.d3 = new DirectoryEntry(key3, "A collision place", cost * 2, quality);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        this.dl = null;
    }

    @Test
    void testInsert() {
        assertTrue(this.dl.add(this.d), "Add good item");
        assertEquals(1, this.dl.size(), "Insert successful");
        final DirectoryEntry o = this.dl.get(0);
        assertNotNull(o, "Object retrieved");
    }

    @Test
    void testInsertDuplicate() {
        assertTrue(this.dl.add(this.d), "Add good item");
        assertTrue(this.dl.add(this.d), "Add another good item");
        assertEquals(1, this.dl.size(), "Inserts successful");
        final DirectoryEntry o = this.dl.get(0);
        assertNotNull(o, "Object retrieved");
    }

    @Test
    void testInsertDifferentPlaceSameDataId() {
        assertTrue(this.dl.add(this.d), "First item inserted");
        assertEquals(1, this.dl.size(), "Item inserted");
        final String nkey = key1.replaceAll("thePlace", "newPlace");
        final DirectoryEntry nd = new DirectoryEntry(nkey, "A new place", cost, quality);
        assertTrue(this.dl.add(nd), "Add item with same data id");
        assertEquals(2, this.dl.size(), "Item inserted");
    }

    @Test
    void testInserts() {
        assertTrue(this.dl.add(this.d), "Add good item");
        assertTrue(this.dl.add(this.d2), "Add another good item");
        assertEquals(2, this.dl.size(), "Inserts successful");
        final DirectoryEntry o = this.dl.get(0);
        assertNotNull(o, "Object retrieved");
        final DirectoryEntry o2 = this.dl.get(1);
        assertNotNull(o2, "Object retrieved");
    }

    @Test
    void testSortedInserts() {
        assertTrue(dl.add(d), "Add good item");
        assertTrue(dl.add(d2), "Add another good item"); // add FOOPLACE first
        assertTrue(dl.add(d3), "Add another good item"); // then add BARPLACE
        assertEquals(3, dl.size(), "Inserts successful");
        DirectoryEntry o = dl.get(0);
        assertNotNull(o, "Object was null");
        DirectoryEntry o2 = dl.get(1);
        assertNotNull(o2, "Object was null");
        assertEquals("BARPLACE", o2.getServiceName(), "Object not sorted correctly");
        DirectoryEntry o3 = dl.get(2);
        assertNotNull(o3, "Object was null");
        assertEquals("FOOPLACE", o3.getServiceName(), "Object not sorted correctly");
    }

    @Test
    void testAddDuplicate() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 50, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals(1, this.dl.size(), "Add duplicate entry");
    }

    @Test
    void testAddDuplicateAsCollection() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 50, 50);
        final List<DirectoryEntry> list = new ArrayList<>();
        list.add(de);
        list.add(de2);
        this.dl.addAll(list);
        assertEquals(1, this.dl.size(), "Add duplicate entry");
    }

    @Test
    void testAddSameCost() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc one", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc two", 50, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals(1, this.dl.size(), "Add same cost entry");
        final DirectoryEntry re = this.dl.getEntry(0);
        assertEquals("desc one", re.getDescription(), "Kept original entry on same cost");
    }

    @Test
    void testAddLowerCost() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 40, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals(1, this.dl.size(), "Add lower cost entry");
        final DirectoryEntry re = this.dl.getEntry(0);
        assertEquals(40, re.getCost(), "Kept lowest cost entry");
    }

    @Test
    void testAddHigherCost() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 60, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals(1, this.dl.size(), "Add higher cost entry");
        final DirectoryEntry re = this.dl.getEntry(0);
        assertEquals(50, re.getCost(), "Kept lowest cost entry");
    }

    @Test
    void testCopy() {
        this.dl.add(this.d);
        this.dl.add(this.d2);
        final DirectoryEntryList dl2 = new DirectoryEntryList();
        dl2.addAll(this.dl);
        assertEquals(this.dl.size(), dl2.size(), "Copy size");

        final DirectoryEntryList dl3 = new DirectoryEntryList(this.dl, true);
        assertEquals(this.dl.size(), dl3.size(), "Copy ctor size");

        // Assure that the copy is deep
        final DirectoryEntry e1 = dl3.get(0);
        e1.setCost(cost * 4);
        assertEquals(cost, this.dl.get(0).getCost(), "Cost of original entry unchanged");

        // Test possible NPE path
        final DirectoryEntryList dl4 = new DirectoryEntryList(null);
        assertEquals(0, dl4.size(), "Copy ctor size on null");
    }

    @Test
    void testXml() throws JDOMException {
        final Document jdom = JDOMUtil.createDocument("<entryList></entryList>", false);
        final Element root = jdom.getRootElement();
        root.addContent(this.d.getXML());
        root.addContent(this.d2.getXML());

        final DirectoryEntryList dl2 = DirectoryEntryList.fromXML(root);
        assertNotNull(dl2, "From xml");
        assertEquals(2, dl2.size(), "Size from xml");
    }

    @Test
    void testSort() {
        final DirectoryEntry de = new DirectoryEntry(key1 + "x", "desc one", 10, 10);
        this.dl.add(this.d);
        this.dl.add(de);
        assertEquals(2, this.dl.size(), "Add cheaper cost entry");
        DirectoryEntry re = this.dl.getEntry(0);
        assertEquals(10, re.getCost(), "Low cost first");

        // Change the cost and re-sort
        re.addCost(5000);
        this.dl.sort();

        re = this.dl.getEntry(0);
        assertEquals(50, re.getCost(), "Low cost entry changed");
    }
}
