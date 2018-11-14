package emissary.core;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AggregateObjectTest extends UnitTest {

    private AggregateObjectTester obj;

    @Override
    @Before
    public void setUp() throws Exception {
        this.obj = new AggregateObjectTester();
        this.obj.testAddFacet(new Facet("Test1"));
        this.obj.testAddFacet(new Facet("Test2"));
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.obj = null;
    }

    @Test
    public void testAddFacet() {
        assertNotNull("Setup did not complete", this.obj);
        assertNotNull("Get facet", this.obj.getFacet("Test1"));
        assertNotNull("Get facet", this.obj.getFacet("Test2"));
        assertNull("Non existent facet", this.obj.getFacet("BogusFacet"));
    }

    @Test
    public void testRemoveFacet() {
        assertNotNull("Setup did not complete", this.obj);
        final Facet f1 = this.obj.testRemoveFacet("Test1");
        assertNotNull("Test1 facet removal", f1);
        assertNull("Bogus facet removal", this.obj.testRemoveFacet("Bogus"));
        final Facet f2 = this.obj.testRemoveFacet("Test2");
        assertNotNull("Test2 facet removal", f2);
        assertNull("Bogus2 facet removal", this.obj.testRemoveFacet("Bogus2"));
    }

    @Test
    public void testFacetOf() {
        assertNotNull("Setup did not complete", this.obj);
        assertNotNull("Get Facet.of", Facet.of(this.obj, "Test1"));
        assertNotNull("Get Facet.of", Facet.of(this.obj, "Test2"));
        assertNull("Non existent Facet.of", Facet.of(this.obj, "BogusFacet"));
    }

    // Class that allows testing the protected methods
    private static final class AggregateObjectTester extends AggregateObject {
        public void testAddFacet(final Facet f) {
            this.addFacet(f);
        }

        public Facet testRemoveFacet(final String name) {
            return this.removeFacet(name);
        }
    }
}
