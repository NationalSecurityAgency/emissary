package emissary.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class ClassComparatorTest extends UnitTest {
    @Test
    void testSameClass() {
        assertTrue(ClassComparator.isa("java.lang.String", String.class.getName()), "Same class name is same");
    }

    @Test
    void testBogusClass() {
        assertFalse(ClassComparator.isa(ClassComparator.class.getName(), "foo.de.Bar"), "Bogus class is not the same as real class");
    }

    @Test
    void testSuperSub() {
        assertTrue(ClassComparator.isa("emissary.place.ServiceProviderPlace", "emissary.place.sample.DevNullPlace"), "Super/sub should pass");
    }

    @Test
    void testIsaImplementationTrue() {
        assertTrue(ClassComparator.isaImplementation(String.class, Serializable.class));
    }

    @Test
    void testIsaImplementationFalse() {
        assertFalse(ClassComparator.isaImplementation(String.class, Iterable.class));
    }
}
