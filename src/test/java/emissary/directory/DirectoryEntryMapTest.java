package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class DirectoryEntryMapTest extends UnitTest {

    private static String key = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/ThePlace";
    private static String key2 = "UNKNOWN.FOOPLACE.ID.http://host2.domain.com:9001/ThePlace";
    private static String key3 = "UNKNOWN.FOOPLACE.TRANSFORM.http://host.domain.com:8001/ThePlace";
    private static int cost = 50;
    private static int quality = 50;

    private DirectoryEntryMap dm = null;
    private DirectoryEntry d = null;
    private DirectoryEntry d2 = null;
    private DirectoryEntry d3 = null;

    @Override
    @Before
    public void setUp() throws Exception {
        this.d = new DirectoryEntry(key, "This is a place", cost, quality);
        this.d2 = new DirectoryEntry(key2, "Another place", cost * 2, quality);
        this.d3 = new DirectoryEntry(key3, "Transform place", cost, quality);
        this.dm = new DirectoryEntryMap();
        this.dm.addEntry(this.d);
        this.dm.addEntry(this.d2);
        this.dm.addEntry(this.d3);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.dm = null;
        this.d = null;
        this.d2 = null;
        this.d3 = null;
    }

    @Test
    public void testAllEntries() {
        final List<DirectoryEntry> all = this.dm.allEntries();
        assertNotNull("All entries not null", all);
        assertEquals("All entries", 3, all.size());
    }

    @Test
    public void testRemoveEntry() {
        // remove first on list
        DirectoryEntry removed = this.dm.removeEntry(this.d.getKey());
        assertNotNull("Removed entry", removed);
        assertEquals("Removed entry", this.d.getKey(), removed.getKey());
        assertEquals("No copy on remove", this.d, removed);

        // Add it back and remove second on list
        this.dm.addEntry(this.d);
        removed = this.dm.removeEntry(this.d2.getKey());
        assertNotNull("Removed entry", removed);
        assertEquals("Remove entry", this.d2.getKey(), removed.getKey());

        // no such entry on list
        DirectoryEntry notfound = this.dm.removeEntry("FOO.BAR.BAZ.http://host:8001/FooPlace");
        assertNull("Removed entry not found", notfound);

        // no such list
        notfound = this.dm.removeEntry("BAR.FOO.BAZ.http://host:8001/FooPlace");
        assertNull("Removed entry not found", notfound);
    }


    @Test
    public void testRemoveAll() {
        assertEquals("Count of matching", 0, this.dm.countAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace"));
        List<DirectoryEntry> removed = this.dm.removeAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace");
        assertNotNull("Removed no matching", removed);
        assertEquals("Removed no matching", 0, removed.size());

        assertEquals("Count of matching", 1, this.dm.countAllMatching(key));
        removed = this.dm.removeAllMatching(key);
        assertNotNull("Removed one matching", removed);
        assertEquals("Removed one matching", 1, removed.size());

        assertEquals("Count of matching", 2, this.dm.countAllMatching("*.*.*.*"));
        removed = this.dm.removeAllMatching("*.*.*.*");
        assertNotNull("Removed all", removed);
        assertEquals("Removed all with wildcard", 2, removed.size());
        assertEquals("All entries should have been removed", 0, this.dm.entryCount());
        assertEquals("Empty mappings should be removed", 0, this.dm.size());
    }

    @Test
    public void testRemoveAllWithTime() {
        assertEquals("Count of matching", 0, this.dm.countAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace"));
        // non-matchting key
        List<DirectoryEntry> removed = this.dm.removeAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace", System.currentTimeMillis());
        assertNotNull("Removed no matching", removed);
        assertEquals("Removed no matching", 0, removed.size());

        // matching key, prehistoric time
        assertEquals("Count of matching", 1, this.dm.countAllMatching(key));
        removed = this.dm.removeAllMatching(key, System.currentTimeMillis() - 3600000);
        assertNotNull("Removed one matching", removed);
        assertEquals("Removed one matching", 0, removed.size());

        // matching key, time
        assertEquals("Count of matching", 1, this.dm.countAllMatching(key));
        removed = this.dm.removeAllMatching(key, this.d.getAge() + 1);
        assertNotNull("Removed one matching", removed);
        assertEquals("Removed one matching", 1, removed.size());


        assertEquals("Count of matching", 2, this.dm.countAllMatching("*.*.*.*"));
        removed = this.dm.removeAllMatching("*.*.*.*");
        assertNotNull("Removed all", removed);
        assertEquals("Removed all with wildcard", 2, removed.size());
        assertEquals("All entries should have been removed", 0, this.dm.entryCount());
        assertEquals("Empty mappings should be removed", 0, this.dm.size());
    }

    @Test
    public void testAllOnDirectory() {
        final String testkey = "ZIP.CHIP.CHOP.http://host.domain.com:8001/DirectoryPlace";
        assertEquals("Count on directory", 2, this.dm.countAllOnDirectory(testkey));
        final List<DirectoryEntry> collected = this.dm.collectAllOnDirectory(testkey);
        final List<DirectoryEntry> removed = this.dm.removeAllOnDirectory(testkey);
        assertNotNull("Collected on dir", collected);
        assertNotNull("All on directory removed", removed);
        assertEquals("Collected matches removed", removed.size(), collected.size());
        assertEquals("All on directory removed", 2, removed.size());
    }

    @Ignore
    @Test
    public void testCollectAllMatchingTiming() {
        // Create a bunch of entries spread out onto several data identifiers
        final String[] phases = new String[] {"ID", "TRANSFORM", "ANALYZE", "IO"};
        final int phasesLength = phases.length;
        final String[] forms = new String[] {"UNKNOWN", "PETERPAN", "WENDY", "CROC", "MR_SMEE"};
        final int formsLength = forms.length;
        for (int i = 1; i < 10000; i++) {
            final StringBuilder sb = new StringBuilder(68);
            sb.append(forms[i % formsLength]);
            sb.append(".FOOPLACE.");
            sb.append(phases[i % phasesLength]);
            sb.append(".http://host-");
            sb.append(i);
            sb.append(".domain.com:7001/ThePlace");
            this.dm.addEntry(new DirectoryEntry(sb.toString(), "blah", i % 100, 50));
            // dm.addEntry(new DirectoryEntry(String.format("%s.FOOPLACE.%s.http://host-%d.domain.com:7001/ThePlace",
            // forms[i % formsLength], phases[i
            // % phasesLength], i), "blah", i % 100, 50));
        }
        final int[] expectedCount = new int[] {2501, 2002, 501, 2002, 501, 10002, 10002, 1};
        final String[] searchPatterns =
                new String[] {"*.*.ID.*", "UNKNOWN.*.*.*", "UNKNOWN.*.ID.*", "?NKNOWN*.*.*.*", "UNKNOWN.*.TRANSFORM.*", "*.FOOPLACE.*.*", "*.*.*.*",
                        "*.FOOPLACE.*.http://host-1001.domain.com:7001/ThePlace"};
        for (int i = 0; i < searchPatterns.length; i++) {
            final long start = System.currentTimeMillis();
            final List<DirectoryEntry> matches = this.dm.collectAllMatching(searchPatterns[i]);
            final long end = System.currentTimeMillis();
            assertEquals("Match must produce results using pattern " + searchPatterns[i], expectedCount[i], matches.size());
            assertTrue("Search using pattern " + searchPatterns[i] + " must be under 1sec", (end - start) < 1000);
        }

        // Simulate the scale of an incoming zone transfer during node setup phase
        final long start = System.currentTimeMillis();
        for (final DirectoryEntry de : this.dm.allEntries()) {
            this.dm.collectAllMatching(de.getKey());
        }
        final long end = System.currentTimeMillis();
        final long diff = (end - start);

        assertTrue("Search of all patterns must take place in under 3s, was " + diff + "ms", diff < 3000);
    }

    @Test
    public void testAddCost() {
        final List<String> changed = this.dm.addCostToMatching("*.*.TRANSFORM.*", 100);
        assertNotNull("Cost changed", changed);
        assertEquals("Cost changed", 1, changed.size());
        final String ckey = changed.get(0);
        assertEquals("Cost on returned entry", DirectoryEntry.calculateExpense(100 + cost, quality), KeyManipulator.getExpense(ckey, -1));
        final DirectoryEntryList del = this.dm.get("UNKNOWN::TRANSFORM");
        assertNotNull("Found entry list for changed entry", del);
        assertEquals("Entry on list", 1, del.size());
        final DirectoryEntry entry = del.getEntry(0);
        assertEquals("Cost on live list entry", 100 + cost, entry.getCost());
    }

    @Test
    public void testAddFromMap() {
        final DirectoryEntry xd = new DirectoryEntry(key + "x", "This is a place", cost, quality);
        final DirectoryEntry xd2 = new DirectoryEntry(key2 + "x", "Another place", cost * 2, quality);
        final DirectoryEntry xd3 = new DirectoryEntry(key3 + "x", "Transform place", cost, quality);
        final String key4 = "A.B.C.http://example.com:1234/SomePlace";
        final DirectoryEntry xd4 = new DirectoryEntry(key4, "ABC Place", cost, quality);
        final DirectoryEntryMap xdm = new DirectoryEntryMap();
        xdm.addEntry(xd);
        xdm.addEntry(xd2);
        xdm.addEntry(xd3);
        xdm.addEntry(xd4);

        // Now add to our regular map
        this.dm.addEntries(xdm);
        assertEquals("Size on addEntries", 3, this.dm.size());
        assertEquals("All entries added", 7, this.dm.allEntries().size());
        assertEquals("All entries added", 7, this.dm.entryCount());
    }

    @Test
    public void testCloneConstructorShallow() {
        // Shallow
        final DirectoryEntryMap dm2 = new DirectoryEntryMap(this.dm);
        assertEquals("Clone size", this.dm.entryCount(), dm2.entryCount());
        for (final String dataId : this.dm.keySet()) {
            final DirectoryEntryList s1 = this.dm.get(dataId);
            final DirectoryEntryList s2 = dm2.get(dataId);
            assertNotNull("List from second map", s2);
            assertEquals("Size of maps is same", s1.size(), s2.size());
        }
    }

    @Test
    public void testCloneConstructorDeep() {
        // Deep
        final DirectoryEntryMap dm3 = new DirectoryEntryMap(this.dm, DirectoryEntryMap.DEEP_COPY);
        assertEquals("Clone size", this.dm.entryCount(), dm3.entryCount());
        for (final String dataId : this.dm.keySet()) {
            final DirectoryEntryList s1 = this.dm.get(dataId);
            final DirectoryEntryList s2 = dm3.get(dataId);
            assertNotNull("List from second map", s2);
            assertEquals("Size of maps is same", s1.size(), s2.size());
        }
    }
}
