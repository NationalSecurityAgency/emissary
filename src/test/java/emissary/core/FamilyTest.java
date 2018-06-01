package emissary.core;

import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Test;

// Family doesn't do much now, but it might later
public class FamilyTest extends UnitTest {

    @Test
    public void testSepValue() {
        assertEquals("Separator birthorder appended", Family.SEP + "5", Family.sep(5));
    }

    @Test
    public void testSepAsMethod() {
        assertEquals("Separator birthorder appended", Family.SEP, Family.sep());
    }

    @Test
    public void testInitial() {
        assertEquals("Attachments start at one", Family.SEP + "1", Family.initial());
    }

    @Test
    public void testNext() {
        Family f = new Family("foo");
        assertEquals("next builds first attachment number", "foo" + Family.SEP + "1", f.next());
        assertEquals("next increments and builds", "foo" + Family.SEP + "2", f.next());
    }

    @Test
    public void testNextWithStartingNum() {
        Family f = new Family("foo", 3);
        assertEquals("next builds specified number", "foo" + Family.SEP + "3", f.next());
        assertEquals("next increments and builds", "foo" + Family.SEP + "4", f.next());
    }

    @Test
    public void testNestingFamily() {
        Family f = new Family("foo");
        Family g = f.child();
        assertEquals("nested family builds one level deeper", "foo" + Family.SEP + "1" + Family.SEP + "1", g.next());
    }
}
