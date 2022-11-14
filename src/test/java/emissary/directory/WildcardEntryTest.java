package emissary.directory;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildcardEntryTest extends UnitTest {

    @Test
    void testPlain() {
        final WildcardEntry w = new WildcardEntry("UNKNOWN::ID");
        final Iterator<String> i = w.iterator();
        assertNotNull(i, "Iterator produced");
        assertTrue(i.hasNext(), "Plain value returned");
        String s = i.next();
        assertEquals("UNKNOWN::ID", s, "Plain value returned unchanged");
        assertTrue(i.hasNext());
        s = i.next();
        assertEquals("*::ID", s, "Plain value wildcard");

        final Set<String> set = w.asSet();
        assertNotNull(set, "Set generation");
        assertEquals(2, set.size(), "Generated set size matches iterator");
    }

    @Test
    void testDashedEntry() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR-BAZ::ID");
        final String[] expected = {"FOO-BAR-BAZ::ID", "FOO-BAR-*::ID", "FOO-*::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue(count < expected.length, "Too many results");
            assertEquals(expected[count], s, "Wildcarded entry " + count);
        }

        assertEquals(count, expected.length, "Too few results");
    }

    @Test
    void testDashedEntryNoServiceType() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR-BAZ");
        final String[] expected = {"FOO-BAR-BAZ", "FOO-BAR-*", "FOO-*", "*"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue(count < expected.length, "Too many results");
            assertEquals(expected[count], s, "Wildcarded entry " + count);
        }

        assertEquals(count, expected.length, "Too few results");
    }

    @Test
    void testParenEntry() {
        final WildcardEntry w = new WildcardEntry("FOO(BAR)::ID");
        final String[] expected = {"FOO(BAR)::ID", "FOO(*)::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue(count < expected.length, "Too many results");
            assertEquals(expected[count], s, "Wildcarded entry " + count);
        }

        assertEquals(count, expected.length, "Too few results");
    }

    @Test
    void testParenEntryNoServiceType() {
        final WildcardEntry w = new WildcardEntry("FOO(BAR)");
        final String[] expected = {"FOO(BAR)", "FOO(*)", "*"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue(count < expected.length, "Too many results");
            assertEquals(expected[count], s, "Wildcarded entry " + count);
        }

        assertEquals(count, expected.length, "Too few results");
    }

    @Test
    void testMultiParenEntry() {
        final WildcardEntry w = new WildcardEntry("FOO(BAR)(BAZ)::ID");
        final String[] expected = {"FOO(BAR)(BAZ)::ID", "FOO(BAR)(*)::ID", "FOO(*)(*)::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue(count < expected.length, "Too many results");
            assertEquals(expected[count], s, "Wildcarded entry " + count);
        }

        assertEquals(count, expected.length, "Too few results");
    }

    @Test
    void testMixedEntry() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR(SHAZAM)-BAZ::ID");
        final String[] expected = {"FOO-BAR(SHAZAM)-BAZ::ID", "FOO-BAR(*)-BAZ::ID", "FOO-BAR(*)-*::ID", "FOO-*::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue(count < expected.length, "Too many results");
            assertEquals(expected[count], s, "Wildcarded entry " + count);
        }

        assertEquals(count, expected.length, "Too few results");
    }


    @Test
    void testMixedEntryWithEmbeddedDash() {
        final WildcardEntry w = new WildcardEntry("FOO-BAR(SHAZAM-ASCII)-BAZ::ID");
        final String[] expected = {"FOO-BAR(SHAZAM-ASCII)-BAZ::ID", "FOO-BAR(*)-BAZ::ID", "FOO-BAR(*)-*::ID", "FOO-*::ID", "*::ID"};
        int count = 0;
        for (Iterator<String> i = w.iterator(); i.hasNext(); count++) {
            final String s = i.next();
            assertTrue(count < expected.length, "Too many results");
            assertEquals(expected[count], s, "Wildcarded entry " + count);
        }

        assertEquals(count, expected.length, "Too few results");
    }
}
