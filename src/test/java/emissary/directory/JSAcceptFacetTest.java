package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import emissary.core.AggregateObject;
import emissary.core.DataObjectFactory;
import emissary.core.Facet;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSAcceptFacetTest extends UnitTest {

    private Logger logger = LoggerFactory.getLogger(JSAcceptFacetTest.class);

    private final DirectoryEntry de = new DirectoryEntry("FOO.BAR.ID.http://example.com:8005/FooPlace");
    private final DirectoryEntry fde1 = new DirectoryEntry("ZAB.ZUB.ID.http://example.com:8005/FooPlace");
    private final DirectoryEntry fde2 = new DirectoryEntry("BAZ.BUZ.ID.http://example.com:8005/FooPlace");
    private final IBaseDataObject payload = DataObjectFactory.getInstance(new Object[] {"foo".getBytes(), "foo.txt", "FOO"});
    private JSAcceptFacet facet;

    @Override
    @Before
    public void setUp() throws Exception {
        this.facet = new JSAcceptFacet();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testAdd() throws Exception {
        assertFalse("No routing before the add", this.facet.containsRoutingFor(this.de));
        assertTrue("ACCEPT_SCRIPT should add", this.facet.add(this.de, ACCEPT_SCRIPT, false));
        assertTrue("Routing should be found after the add", this.facet.containsRoutingFor(this.de));
        assertFalse("ACCEPT_SCRIPT should not remove as default", this.facet.removeDefault(this.de));
        assertTrue("ACCEPT_SCRIPT should remove", this.facet.remove(this.de));
        assertFalse("No routing after remove", this.facet.containsRoutingFor(this.de));
    }

    @Test
    public void testAddAsDefault() throws Exception {
        assertFalse("No routing before the add", this.facet.containsRoutingFor(this.de));
        assertTrue("ACCEPT_SCRIPT should add", this.facet.add(this.de, ACCEPT_SCRIPT, true));
        assertTrue("Routing should be found after the add", this.facet.containsRoutingFor(this.de));
        assertFalse("ACCEPT_SCRIPT should not remove as non-default", this.facet.remove(this.de));
        assertTrue("ACCEPT_SCRIPT should remove as default", this.facet.removeDefault(this.de));
        assertFalse("No routing after remove", this.facet.containsRoutingFor(this.de));
    }

    @Test
    public void testBrokenScripts() {
        try {
            this.facet.add(this.de, BROKEN_SCRIPT, false);
            fail("Should throw exception when adding broken script");
        } catch (Exception ignore) {
            // Ignore.
        }

        try {
            assertFalse("Should not add func with wrong name", this.facet.add(this.de, WRONG_FUNC_SCRIPT, false));
        } catch (Exception ex) {
            fail("Shoudl not throw exception when script merely has wrong name");
        }

        assertFalse("No routing for broken scripts", this.facet.containsRoutingFor(this.de));
    }

    @Test
    public void testAcceptFunction() throws Exception {
        final List<DirectoryEntry> itinerary = new ArrayList<DirectoryEntry>();
        itinerary.add(this.de);

        this.facet.accept(this.payload, itinerary);
        assertEquals("No change before adding script", 1, itinerary.size());

        this.facet.add(this.de, ACCEPT_SCRIPT, false);
        this.facet.accept(this.payload, itinerary);
        assertEquals("No change after adding ACCEPT_SCRIPT", 1, itinerary.size());
        this.facet.remove(this.de);
        assertFalse("No routing after remove", this.facet.containsRoutingFor(this.de));

        this.facet.add(this.de, ACCEPT_SCRIPT, true);
        this.facet.accept(this.payload, itinerary);
        assertEquals("No change after adding default ACCEPT_SCRIPT", 1, itinerary.size());
        this.facet.removeDefault(this.de);
        assertFalse("No routing after remove", this.facet.containsRoutingFor(this.de));

        this.logger.debug("Adding reject script non-default on key " + this.de + " itin " + itinerary);
        assertTrue("Should add reject script", this.facet.add(this.de, REJECT_SCRIPT, false));
        this.facet.accept(this.payload, itinerary);
        assertEquals("Removed after adding REJECT_SCRIPT", 0, itinerary.size());
        assertTrue("Should remove reject script", this.facet.remove(this.de));
        assertFalse("No routing after remove", this.facet.containsRoutingFor(this.de));
        itinerary.add(this.de);

        this.logger.debug("Adding reject script default on key " + this.de + " itin " + itinerary);
        assertTrue("Should add default reject script", this.facet.add(this.de, REJECT_SCRIPT, true));
        this.facet.accept(this.payload, itinerary);
        assertEquals("Removed after after adding default REJECT_SCRIPT", 0, itinerary.size());
        assertTrue("Should remove default reject script", this.facet.removeDefault(this.de));
        assertFalse("No routing after remove", this.facet.containsRoutingFor(this.de));
        itinerary.add(this.de);
    }

    @Test
    public void testFileResources() {
        this.facet.setBasePackage(thisPackage.getName() + ".");
        final List<DirectoryEntry> itinerary = new ArrayList<DirectoryEntry>();

        itinerary.add(this.fde1); // reject
        itinerary.add(this.de); // accept
        itinerary.add(this.fde2); // reject

        assertFalse("Routing rules not present until needed", this.facet.containsRoutingFor(this.fde1));
        assertFalse("Routing rules not present until needed", this.facet.containsRoutingFor(this.de));
        assertFalse("Routing rules not present until needed", this.facet.containsRoutingFor(this.fde2));

        this.facet.accept(this.payload, itinerary);

        assertTrue("Routing rules loaded on demand", this.facet.containsRoutingFor(this.fde1));
        assertFalse("Routing rules not present until needed", this.facet.containsRoutingFor(this.de));
        assertTrue("Routing rules loaded on demand", this.facet.containsRoutingFor(this.fde2));

        assertEquals("File based rules should have rejected items", 1, itinerary.size());
        this.facet.setBasePackage(JSAcceptFacet.DEFAULT_BASE_PACKAGE);
    }

    @Test
    public void testFacetOf() throws Exception {
        final TestAggregateObject obj = new TestAggregateObject();
        assertNull("No such facet can be found", JSAcceptFacet.of(null));
        assertNull("No such facet can be found", JSAcceptFacet.of(obj));
        obj.testAddFacet(this.facet);
        assertNotNull("Facet must be found", JSAcceptFacet.of(obj));
    }

    static class TestAggregateObject extends AggregateObject {
        public void testAddFacet(final Facet f) {
            super.addFacet(f);
        }
    }

    public static final String REJECT_SCRIPT = "function accept(payload) { return false; }";

    public static final String ACCEPT_SCRIPT = "function accept(payload) { return true; }";

    public static final String WRONG_FUNC_SCRIPT = "function wrong_name(payload) {return 'yes it is';}";

    public static final String BROKEN_SCRIPT = "function accept(payload) { return hooba;";
}
