package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Test;

public class NamespaceTest extends UnitTest {

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        for (Iterator<String> i = Namespace.keySet().iterator(); i.hasNext();) {
            Namespace.unbind(i.next());
        }
    }

    @Test
    public void testEmpty() {
        assertThrows(NamespaceException.class,
                () -> Namespace.lookup("myObject"),
                "Found object in empty namespace ");
    }

    @Test
    public void testSingie() {
        final Object one = new Object();
        Namespace.bind("myObject1", one);
        try {
            final Object o = Namespace.lookup("myObject1");
            assertEquals("Namespace retrieval", one, o);
        } catch (NamespaceException e) {
            fail("Object not found: " + e.toString());
        }
    }

    @Test
    public void testUnbind() {
        Namespace.bind("myObject2", new Object());
        Namespace.unbind("myObject2");
        assertThrows(NamespaceException.class,
                () -> Namespace.lookup("myObject2"),
                "Failed to unbind object from namespace");
    }

    @Test
    public void testTailMatchLookup() {
        final Object thePlace = new Object();
        Namespace.bind("http://machine:8001/StuffPlace", thePlace);
        try {
            final Object o = Namespace.lookup("StuffPlace");
            assertEquals("Tail match on Namespace lookup", thePlace, o);
        } catch (NamespaceException e) {
            fail("Lookup failed: " + e.getMessage());
        }
        assertThrows(NamespaceException.class, () -> Namespace.lookup("BadStuffPlace"));
    }

    @Test
    public void testExists() {
        final Object thePlace = new Object();
        Namespace.bind("http://machine:8001/StuffPlace", thePlace);
        assertTrue("Full key lookup", Namespace.exists("http://machine:8001/StuffPlace"));
        assertTrue("Tail lookup", Namespace.exists("StuffPlace"));
        assertFalse("Bad full key lookup", Namespace.exists("http://machine:8001/BadStuffPlace"));
        assertFalse("Bad ail lookup", Namespace.exists("BadStuffPlace"));
    }

    @Test
    public void testNameOverlap() {
        final Object[] a = {new Object(), new Object(), new Object(), new Object(), new Object()};
        final String[] name = {"a", "aa", "xxa", "xxaxx", "axx"};

        // register all
        for (int i = 0; i < a.length; i++) {
            Namespace.bind(name[i], a[i]);
        }

        // Find the one that shoud work
        try {
            final Object o = Namespace.lookup("a");
            assertEquals("Found by name", a[0], o);
        } catch (NamespaceException e) {
            fail("Could not find object a " + e.toString());
        }
    }
}
