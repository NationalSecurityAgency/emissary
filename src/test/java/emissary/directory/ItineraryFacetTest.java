package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class ItineraryFacetTest extends UnitTest {

    private IBaseDataObject payload = DataObjectFactory.getInstance();

    @Test
    public void testCreate() {
        final ItineraryFacet f = new ItineraryFacet();
        assertEquals("Naming the facet", f.getName(), ItineraryFacet.ITINERARY_FACET_NAME);
    }

    @Test
    public void testConfiguration() throws IOException {
        final String cdata = "FACET = \"" + TestItineraryFace.class.getName() + "\"\n";
        final InputStream is = new ByteArrayInputStream(cdata.getBytes());
        final Configurator config = new ServiceConfigGuide(is);
        final ItineraryFacet f = new ItineraryFacet(config);
        assertEquals("One face created with two types", 2, f.size());
        final DirectoryEntryList del = new DirectoryEntryList();
        final DirectoryEntryMap dem = new DirectoryEntryMap();
        f.thinkOn("FOO::ID", this.payload, del, dem);
        assertEquals("Process counter incremented", 1, TestItineraryFace.processCounter);
        f.thinkOn("BAZ::ID", this.payload, del, dem);
        assertEquals("Process counter not incremented", 1, TestItineraryFace.processCounter);
    }

    @Test
    public void testItineraryFaceImpl() {
        final TestItineraryFace f = new TestItineraryFace();
        f.runRegisterType("SHAZAM::STUDY");
        assertTrue("Type got registered", f.isRegistered("SHAZAM::STUDY"));
        assertTrue("No such type registered", !f.isRegistered("BOGUS::OUTPUT"));

        final List<DirectoryEntry> del = new ArrayList<DirectoryEntry>();
        final String k1 = "FOO.FOOPLACE.ID.http://example.com:8888/FooPlace$1234";
        final String k2 = "BAR.BARPLACE.TRANSFORM.http://example.com:8888/BarPlace$5678";
        final DirectoryEntry d1 = new DirectoryEntry(k1);
        final DirectoryEntry d2 = new DirectoryEntry(k2);
        del.add(d1);
        del.add(d2);
        final DirectoryEntry de = f.lastStep(null);
        assertNull("No such directory entry", de);
        final DirectoryEntry dlast = f.lastStep(del);
        assertNotNull("Last entry chosen", dlast);
        assertEquals("Last entry from itinerary", k2, dlast.getFullKey());

        final DirectoryEntryMap dem = new DirectoryEntryMap();
        dem.addEntry(d1);
        dem.addEntry(d2);

        DirectoryEntry fselected = f.runSelect(d2, dem, "FOO::ID");
        assertNull("No FOO more expensive than BAR", fselected);
        fselected = f.runSelect(d2, dem, "FOO::ID", 0, Integer.MAX_VALUE);
        assertNotNull("FOO should be selected", fselected);
        assertEquals("FOO should be selected", "FOO", fselected.getDataType());
    }

    // Helper class for testing the face impl
    public static class TestItineraryFace extends ItineraryFaceImpl {

        public static int typeCounter = 0;
        public static int processCounter = 0;

        public TestItineraryFace() {
            typeCounter = 0;
            processCounter = 0;
            registerType("FOO::ID");
            registerType("BAR::ID");
        }

        @Override
        public Collection<String> getTypes() {
            typeCounter++;
            return super.getTypes();
        }

        @Override
        public void process(final String dataType, final IBaseDataObject payload, final List<DirectoryEntry> itinerary,
                final DirectoryEntryMap entryMap) {
            processCounter++;
            assertNotNull("Payload in facet is not null", payload);
            assertNotNull("Data type in facet is not null", dataType);
            assertNotNull("entryMap in facet is not null", entryMap);
            assertNotNull("DirectoryEntry in facet is not null", itinerary);
            super.process(dataType, payload, itinerary, entryMap);
        }

        public void runRegisterType(final String type) {
            registerType(type);
        }

        public DirectoryEntry runSelect(final DirectoryEntry sde, final DirectoryEntryMap entries, final String key) {
            return select(sde, entries, key);
        }

        public DirectoryEntry runSelect(final DirectoryEntry sde, final DirectoryEntryMap entries, final String key, final int minExpense,
                final int maxExpense) {
            return select(sde, entries, key, minExpense, maxExpense);
        }
    }
}
