package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import emissary.test.core.UnitTest;
import emissary.util.xml.JDOMUtil;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DirectoryEntryListTest extends UnitTest {

    private DirectoryEntryList dl = null;
    private static String key1 = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/thePlace";
    private static String key2 = "UNKNOWN.FOOPLACE.TRANSFORM.http://host.domain.com:8001/otherPlace";
    private static String key3 = "UNKNOWN.BARPLACE.TRANSFORM.http://host.domain.com:8001/barPlace";
    private static int cost = 50;
    private static int quality = 50;

    private DirectoryEntry d = null;
    private DirectoryEntry d2 = null;
    private DirectoryEntry d3 = null;

    @Override
    @Before
    public void setUp() throws Exception {
        this.dl = new DirectoryEntryList();
        this.d = new DirectoryEntry(key1, "This is a place", cost, quality);
        this.d2 = new DirectoryEntry(key2, "Another place", cost * 2, quality);
        this.d3 = new DirectoryEntry(key3, "A collision place", cost * 2, quality);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.dl = null;
    }

    @Test
    public void testInsert() {
        assertTrue("Add good item", this.dl.add(this.d));
        assertEquals("Insert successful", 1, this.dl.size());
        final Object o = this.dl.get(0);
        assertNotNull("Object retrieved", o);
        assertTrue("Object retrieved", o instanceof DirectoryEntry);
    }

    @Test
    public void testInsertDuplicate() {
        assertTrue("Add good item", this.dl.add(this.d));
        assertTrue("Add another good item", this.dl.add(this.d));
        assertEquals("Inserts successful", 1, this.dl.size());
        final Object o = this.dl.get(0);
        assertNotNull("Object retrieved", o);
        assertTrue("Object retrieved", o instanceof DirectoryEntry);
    }

    @Test
    public void testInsertDifferentPlaceSameDataId() {
        assertTrue("First item inserted", this.dl.add(this.d));
        assertEquals("Item inserted", 1, this.dl.size());
        final String nkey = key1.replaceAll("thePlace", "newPlace");
        final DirectoryEntry nd = new DirectoryEntry(nkey, "A new place", cost, quality);
        assertTrue("Add item with same data id", this.dl.add(nd));
        assertEquals("Item inserted", 2, this.dl.size());
    }

    @Test
    public void testInserts() {
        assertTrue("Add good item", this.dl.add(this.d));
        assertTrue("Add another good item", this.dl.add(this.d2));
        assertEquals("Inserts successful", 2, this.dl.size());
        final Object o = this.dl.get(0);
        assertNotNull("Object retrieved", o);
        assertTrue("Object retrieved", o instanceof DirectoryEntry);
        final Object o2 = this.dl.get(1);
        assertNotNull("Object retrieved", o2);
        assertTrue("Object retrieved", o2 instanceof DirectoryEntry);
    }

    @Test
    public void testSortedInserts() {
        assertTrue("Add good item", dl.add(d));
        assertTrue("Add another good item", dl.add(d2)); // add FOOPLACE first
        assertTrue("Add another good item", dl.add(d3)); // then add BARPLACE
        assertEquals("Inserts successful", 3, dl.size());
        Object o = dl.get(0);
        assertNotNull("Object was null", o);
        assertTrue("Object instanceof error", o instanceof DirectoryEntry);
        Object o2 = dl.get(1);
        assertNotNull("Object was null", o2);
        assertTrue("Object instanceof error", o2 instanceof DirectoryEntry);
        assertEquals("Object not sorted correctly", "BARPLACE", ((DirectoryEntry) o2).getServiceName());
        Object o3 = dl.get(2);
        assertNotNull("Object was null", o3);
        assertTrue("Object instanceof error", o3 instanceof DirectoryEntry);
        assertEquals("Object not sorted correctly", "FOOPLACE", ((DirectoryEntry) o3).getServiceName());
    }

    @Test
    public void testAddDuplicate() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 50, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals("Add duplicate entry", 1, this.dl.size());
    }

    @Test
    public void testAddDuplicateAsCollection() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 50, 50);
        final List<DirectoryEntry> list = new ArrayList<DirectoryEntry>();
        list.add(de);
        list.add(de2);
        this.dl.addAll(list);
        assertEquals("Add duplicate entry", 1, this.dl.size());
    }

    @Test
    public void testAddSameCost() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc one", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc two", 50, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals("Add same cost entry", 1, this.dl.size());
        final DirectoryEntry re = this.dl.getEntry(0);
        assertEquals("Kept original entry on same cost", "desc one", re.getDescription());
    }

    @Test
    public void testAddLowerCost() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 40, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals("Add lower cost entry", 1, this.dl.size());
        final DirectoryEntry re = this.dl.getEntry(0);
        assertEquals("Kept lowest cost entry", 40, re.getCost());
    }

    @Test
    public void testAddHigherCost() {
        final DirectoryEntry de = new DirectoryEntry(key1, "desc", 50, 50);
        final DirectoryEntry de2 = new DirectoryEntry(key1, "desc", 60, 50);
        this.dl.add(de);
        this.dl.add(de2);
        assertEquals("Add higher cost entry", 1, this.dl.size());
        final DirectoryEntry re = this.dl.getEntry(0);
        assertEquals("Kept lowest cost entry", 50, re.getCost());
    }

    @Test
    public void testCopy() {
        this.dl.add(this.d);
        this.dl.add(this.d2);
        final DirectoryEntryList dl2 = new DirectoryEntryList();
        dl2.addAll(this.dl);
        assertEquals("Copy size", this.dl.size(), dl2.size());

        final DirectoryEntryList dl3 = new DirectoryEntryList(this.dl, true);
        assertEquals("Copy ctor size", this.dl.size(), dl3.size());

        // Assure that the copy is deep
        final DirectoryEntry e1 = dl3.get(0);
        e1.setCost(cost * 4);
        assertEquals("Cost of original entry unchanged", cost, this.dl.get(0).getCost());

        // Test possible NPE path
        final DirectoryEntryList dl4 = new DirectoryEntryList(null);
        assertEquals("Copy ctor size on null", 0, dl4.size());
    }

    @Test
    public void testXml() throws JDOMException {
        final Document jdom = JDOMUtil.createDocument("<entryList></entryList>", false);
        final Element root = jdom.getRootElement();
        root.addContent(this.d.getXML());
        root.addContent(this.d2.getXML());

        final DirectoryEntryList dl2 = DirectoryEntryList.fromXML(root);
        assertNotNull("From xml", dl2);
        assertEquals("Size from xml", 2, dl2.size());
    }

    @Test
    public void testSort() {
        final DirectoryEntry de = new DirectoryEntry(key1 + "x", "desc one", 10, 10);
        this.dl.add(this.d);
        this.dl.add(de);
        assertEquals("Add cheaper cost entry", 2, this.dl.size());
        DirectoryEntry re = this.dl.getEntry(0);
        assertEquals("Low cost first", 10, re.getCost());

        // Change the cost and re-sort
        re.addCost(5000);
        this.dl.sort();

        re = this.dl.getEntry(0);
        assertEquals("Low cost entry changed", 50, re.getCost());
    }
}
