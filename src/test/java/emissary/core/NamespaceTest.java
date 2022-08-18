package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NamespaceTest extends UnitTest {

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        for (String s : Namespace.keySet()) {
            Namespace.unbind(s);
        }
    }

    @Test
    void testEmpty() {
        assertThrows(NamespaceException.class,
                () -> Namespace.lookup("myObject"),
                "Found object in empty namespace ");
    }

    @Test
    void testSingie() {
        final Object one = new Object();
        Namespace.bind("myObject1", one);
        try {
            final Object o = Namespace.lookup("myObject1");
            assertEquals(one, o, "Namespace retrieval");
        } catch (NamespaceException e) {
            fail("Object not found: " + e);
        }
    }

    @Test
    void testUnbind() {
        Namespace.bind("myObject2", new Object());
        Namespace.unbind("myObject2");
        assertThrows(NamespaceException.class,
                () -> Namespace.lookup("myObject2"),
                "Failed to unbind object from namespace");
    }

    @Test
    void testTailMatchLookup() {
        final Object thePlace = new Object();
        Namespace.bind("http://machine:8001/StuffPlace", thePlace);
        try {
            final Object o = Namespace.lookup("StuffPlace");
            assertEquals(thePlace, o, "Tail match on Namespace lookup");
        } catch (NamespaceException e) {
            fail("Lookup failed: " + e.getMessage());
        }
        assertThrows(NamespaceException.class, () -> Namespace.lookup("BadStuffPlace"));
    }

    @Test
    void testExists() {
        final Object thePlace = new Object();
        Namespace.bind("http://machine:8001/StuffPlace", thePlace);
        assertTrue(Namespace.exists("http://machine:8001/StuffPlace"), "Full key lookup");
        assertTrue(Namespace.exists("StuffPlace"), "Tail lookup");
        assertFalse(Namespace.exists("http://machine:8001/BadStuffPlace"), "Bad full key lookup");
        assertFalse(Namespace.exists("BadStuffPlace"), "Bad ail lookup");
    }

    @Test
    void testNameOverlap() {
        final Object[] a = {new Object(), new Object(), new Object(), new Object(), new Object()};
        final String[] name = {"a", "aa", "xxa", "xxaxx", "axx"};

        // register all
        for (int i = 0; i < a.length; i++) {
            Namespace.bind(name[i], a[i]);
        }

        // Find the one that shoud work
        try {
            final Object o = Namespace.lookup("a");
            assertEquals(a[0], o, "Found by name");
        } catch (NamespaceException e) {
            fail("Could not find object a " + e);
        }
    }
}
