package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class WildcardEntryTest extends UnitTest {

    @Test
    public void testPlain() {
        final WildcardEntry w = new WildcardEntry("UNKNOWN::ID");
        final Iterator<String> i = w.iterator();
        assertNotNull("Iterator produced", i);
        assertTrue("Plain value returned", i.hasNext());
        String s = i.next();
        assertEquals("Plain value returned unchanged", "UNKNOWN::ID", s);
        assertTrue(i.hasNext());
        s = i.next();
        assertEquals("Plain value wildcard", "*::ID", s);

        final Set<String> set = w.asSet();
        assertNotNull("Set generation", set);
        assertEquals("Generated set size matches iterator", 2, set.size());
    }

    @Test
    public void testDashedEntry() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR-BAZ::ID");
        final String[] expected = {"FOO-BAR-BAZ::ID", "FOO-BAR-*::ID", "FOO-*::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue("Too many results", count < expected.length);
            assertEquals("Wildcarded entry " + count, expected[count], s);
        }

        assertTrue("Too few results", count == expected.length);
    }

    @Test
    public void testDashedEntryNoServiceType() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR-BAZ");
        final String[] expected = {"FOO-BAR-BAZ", "FOO-BAR-*", "FOO-*", "*"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue("Too many results", count < expected.length);
            assertEquals("Wildcarded entry " + count, expected[count], s);
        }

        assertTrue("Too few results", count == expected.length);
    }

    @Test
    public void testParenEntry() {
        final WildcardEntry w = new WildcardEntry("FOO(BAR)::ID");
        final String[] expected = {"FOO(BAR)::ID", "FOO(*)::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue("Too many results", count < expected.length);
            assertEquals("Wildcarded entry " + count, expected[count], s);
        }

        assertTrue("Too few results", count == expected.length);
    }

    @Test
    public void testParenEntryNoServiceType() {
        final WildcardEntry w = new WildcardEntry("FOO(BAR)");
        final String[] expected = {"FOO(BAR)", "FOO(*)", "*"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue("Too many results", count < expected.length);
            assertEquals("Wildcarded entry " + count, expected[count], s);
        }

        assertTrue("Too few results", count == expected.length);
    }

    @Test
    public void testMultiParenEntry() {
        final WildcardEntry w = new WildcardEntry("FOO(BAR)(BAZ)::ID");
        final String[] expected = {"FOO(BAR)(BAZ)::ID", "FOO(BAR)(*)::ID", "FOO(*)(*)::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue("Too many results", count < expected.length);
            assertEquals("Wildcarded entry " + count, expected[count], s);
        }

        assertTrue("Too few results", count == expected.length);
    }

    @Test
    public void testMixedEntry() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR(SHAZAM)-BAZ::ID");
        final String[] expected = {"FOO-BAR(SHAZAM)-BAZ::ID", "FOO-BAR(*)-BAZ::ID", "FOO-BAR(*)-*::ID", "FOO-*::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue("Too many results", count < expected.length);
            assertEquals("Wildcarded entry " + count, expected[count], s);
        }

        assertTrue("Too few results", count == expected.length);
    }


    @Test
    public void testMixedEntryWithEmbeddedDash() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR(SHAZAM-ASCII)-BAZ::ID");
        final String[] expected = {"FOO-BAR(SHAZAM-ASCII)-BAZ::ID", "FOO-BAR(*)-BAZ::ID", "FOO-BAR(*)-*::ID", "FOO-*::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue("Too many results", count < expected.length);
            assertEquals("Wildcarded entry " + count, expected[count], s);
        }

        assertTrue("Too few results", count == expected.length);
    }
}
