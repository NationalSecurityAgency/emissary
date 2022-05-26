package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

// Family doesn't do much now, but it might later
class FamilyTest extends UnitTest {

    @Test
    void testSepValue() {
        assertEquals(Family.SEP + "5", Family.sep(5), "Separator birthorder appended");
    }

    @Test
    void testSepAsMethod() {
        assertEquals(Family.SEP, Family.sep(), "Separator birthorder appended");
    }

    @Test
    void testInitial() {
        assertEquals(Family.SEP + "1", Family.initial(), "Attachments start at one");
    }

    @Test
    void testNext() {
        Family f = new Family("foo");
        assertEquals("foo" + Family.SEP + "1", f.next(), "next builds first attachment number");
        assertEquals("foo" + Family.SEP + "2", f.next(), "next increments and builds");
    }

    @Test
    void testNextWithStartingNum() {
        Family f = new Family("foo", 3);
        assertEquals("foo" + Family.SEP + "3", f.next(), "next builds specified number");
        assertEquals("foo" + Family.SEP + "4", f.next(), "next increments and builds");
    }

    @Test
    void testNestingFamily() {
        Family f = new Family("foo");
        Family g = f.child();
        assertEquals("foo" + Family.SEP + "1" + Family.SEP + "1", g.next(), "nested family builds one level deeper");
    }
}
