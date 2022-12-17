package emissary.directory;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryEntryMapTest extends UnitTest {

    private static final String key = "UNKNOWN.FOOPLACE.ID.http://host.domain.com:8001/ThePlace";
    private static final String key2 = "UNKNOWN.FOOPLACE.ID.http://host2.domain.com:9001/ThePlace";
    private static final String key3 = "UNKNOWN.FOOPLACE.TRANSFORM.http://host.domain.com:8001/ThePlace";
    private static final int cost = 50;
    private static final int quality = 50;

    private DirectoryEntryMap dm = null;
    private DirectoryEntry d = null;
    private DirectoryEntry d2 = null;
    private DirectoryEntry d3 = null;

    @Override
    @BeforeEach
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
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        this.dm = null;
        this.d = null;
        this.d2 = null;
        this.d3 = null;
    }

    @Test
    void testAllEntries() {
        final List<DirectoryEntry> all = this.dm.allEntries();
        assertNotNull(all, "All entries not null");
        assertEquals(3, all.size(), "Unexpected list size");
    }

    @Test
    void testRemoveEntry() {
        // remove first on list
        DirectoryEntry removed = this.dm.removeEntry(this.d.getKey());
        assertNotNull(removed, "Removed entry");
        assertEquals(this.d.getKey(), removed.getKey(), "Removed entry");
        assertEquals(this.d, removed, "No copy on remove");

        // Add it back and remove second on list
        this.dm.addEntry(this.d);
        removed = this.dm.removeEntry(this.d2.getKey());
        assertNotNull(removed, "Removed entry");
        assertEquals(this.d2.getKey(), removed.getKey(), "Remove entry");

        // no such entry on list
        DirectoryEntry notfound = this.dm.removeEntry("FOO.BAR.BAZ.http://host:8001/FooPlace");
        assertNull(notfound, "Removed entry not found");

        // no such list
        notfound = this.dm.removeEntry("BAR.FOO.BAZ.http://host:8001/FooPlace");
        assertNull(notfound, "Removed entry not found");
    }


    @Test
    void testRemoveAll() {
        assertEquals(0, this.dm.countAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace"), "Count of matching");
        List<DirectoryEntry> removed = this.dm.removeAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace");
        assertNotNull(removed, "Removed no matching");
        assertEquals(0, removed.size(), "Removed no matching");

        assertEquals(1, this.dm.countAllMatching(key), "Count of matching");
        removed = this.dm.removeAllMatching(key);
        assertNotNull(removed, "Removed one matching");
        assertEquals(1, removed.size(), "Removed one matching");

        assertEquals(2, this.dm.countAllMatching("*.*.*.*"), "Count of matching");
        removed = this.dm.removeAllMatching("*.*.*.*");
        assertNotNull(removed, "Removed all");
        assertEquals(2, removed.size(), "Removed all with wildcard");
        assertEquals(0, this.dm.entryCount(), "All entries should have been removed");
        assertEquals(0, this.dm.size(), "Empty mappings should be removed");
    }

    @Test
    void testRemoveAllWithTime() {
        assertEquals(0, this.dm.countAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace"), "Count of matching");
        // non-matchting key
        List<DirectoryEntry> removed = this.dm.removeAllMatching("FOO.BAR.BAZ.http://host:8001/FooPlace", System.currentTimeMillis());
        assertNotNull(removed, "Removed no matching");
        assertEquals(0, removed.size(), "Removed no matching");

        // matching key, prehistoric time
        assertEquals(1, this.dm.countAllMatching(key), "Count of matching");
        removed = this.dm.removeAllMatching(key, System.currentTimeMillis() - 3600000);
        assertNotNull(removed, "Removed one matching");
        assertEquals(0, removed.size(), "Removed one matching");

        // matching key, time
        assertEquals(1, this.dm.countAllMatching(key), "Count of matching");
        removed = this.dm.removeAllMatching(key, this.d.getAge() + 1);
        assertNotNull(removed, "Removed one matching");
        assertEquals(1, removed.size(), "Removed one matching");


        assertEquals(2, this.dm.countAllMatching("*.*.*.*"), "Count of matching");
        removed = this.dm.removeAllMatching("*.*.*.*");
        assertNotNull(removed, "Removed all");
        assertEquals(2, removed.size(), "Removed all with wildcard");
        assertEquals(0, this.dm.entryCount(), "All entries should have been removed");
        assertEquals(0, this.dm.size(), "Empty mappings should be removed");
    }

    @Test
    void testAllOnDirectory() {
        final String testkey = "ZIP.CHIP.CHOP.http://host.domain.com:8001/DirectoryPlace";
        assertEquals(2, this.dm.countAllOnDirectory(testkey), "Count on directory");
        final List<DirectoryEntry> collected = this.dm.collectAllOnDirectory(testkey);
        final List<DirectoryEntry> removed = this.dm.removeAllOnDirectory(testkey);
        assertNotNull(collected, "Collected on dir");
        assertNotNull(removed, "All on directory removed");
        assertEquals(removed.size(), collected.size(), "Collected matches removed");
        assertEquals(2, removed.size(), "All on directory removed");
    }

    @Disabled
    @Test
    void testCollectAllMatchingTiming() {
        // Create a bunch of entries spread out onto several data identifiers
        final String[] phases = new String[] {"ID", "TRANSFORM", "ANALYZE", "IO"};
        final int phasesLength = phases.length;
        final String[] forms = new String[] {"UNKNOWN", "PETERPAN", "WENDY", "CROC", "MR_SMEE"};
        final int formsLength = forms.length;
        for (int i = 1; i < 10000; i++) {
            String sb = forms[i % formsLength] +
                    ".FOOPLACE." +
                    phases[i % phasesLength] +
                    ".http://host-" +
                    i +
                    ".domain.com:7001/ThePlace";
            this.dm.addEntry(new DirectoryEntry(sb, "blah", i % 100, 50));
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
            assertEquals(expectedCount[i], matches.size(), "Match must produce results using pattern " + searchPatterns[i]);
            assertTrue((end - start) < 1000, "Search using pattern " + searchPatterns[i] + " must be under 1sec");
        }

        // Simulate the scale of an incoming zone transfer during node setup phase
        final long start = System.currentTimeMillis();
        for (final DirectoryEntry de : this.dm.allEntries()) {
            this.dm.collectAllMatching(de.getKey());
        }
        final long end = System.currentTimeMillis();
        final long diff = (end - start);

        assertTrue(diff < 3000, "Search of all patterns must take place in under 3s, was " + diff + "ms");
    }

    @Test
    void testAddCost() {
        final List<String> changed = this.dm.addCostToMatching("*.*.TRANSFORM.*", 100);
        assertNotNull(changed, "Cost changed");
        assertEquals(1, changed.size(), "Cost changed");
        final String ckey = changed.get(0);
        assertEquals(DirectoryEntry.calculateExpense(100 + cost, quality), KeyManipulator.getExpense(ckey, -1), "Cost on returned entry");
        final DirectoryEntryList del = this.dm.get("UNKNOWN::TRANSFORM");
        assertNotNull(del, "Found entry list for changed entry");
        assertEquals(1, del.size(), "Entry on list");
        final DirectoryEntry entry = del.getEntry(0);
        assertEquals(100 + cost, entry.getCost(), "Cost on live list entry");
    }

    @Test
    void testAddFromMap() {
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
        assertEquals(3, this.dm.size(), "Size on addEntries");
        assertEquals(7, this.dm.allEntries().size(), "All entries added");
        assertEquals(7, this.dm.entryCount(), "All entries added");
    }

    @Test
    void testCloneConstructorShallow() {
        // Shallow
        final DirectoryEntryMap dm2 = new DirectoryEntryMap(this.dm);
        assertEquals(this.dm.entryCount(), dm2.entryCount(), "Clone size");
        for (final String dataId : this.dm.keySet()) {
            final DirectoryEntryList s1 = this.dm.get(dataId);
            final DirectoryEntryList s2 = dm2.get(dataId);
            assertNotNull(s2, "List from second map");
            assertEquals(s1.size(), s2.size(), "Size of maps is same");
        }
    }

    @Test
    void testCloneConstructorDeep() {
        // Deep
        final DirectoryEntryMap dm3 = new DirectoryEntryMap(this.dm, DirectoryEntryMap.DEEP_COPY);
        assertEquals(this.dm.entryCount(), dm3.entryCount(), "Clone size");
        for (final String dataId : this.dm.keySet()) {
            final DirectoryEntryList s1 = this.dm.get(dataId);
            final DirectoryEntryList s2 = dm3.get(dataId);
            assertNotNull(s2, "List from second map");
            assertEquals(s1.size(), s2.size(), "Size of maps is same");
        }
    }
}
