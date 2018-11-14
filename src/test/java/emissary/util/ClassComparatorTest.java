package emissary.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class ClassComparatorTest extends UnitTest {
    @Test
    public void testSameClass() {
        assertTrue("Same class name is same", ClassComparator.isa("java.lang.String", String.class.getName()));
    }

    @Test
    public void testBogusClass() {
        assertFalse("Bogus class is not the same as real class", ClassComparator.isa(ClassComparator.class.getName(), "foo.de.Bar"));
    }

    @Test
    public void testSuperSub() {
        assertTrue("Super/sub should pass", ClassComparator.isa("emissary.place.ServiceProviderPlace", "emissary.place.sample.DevNullPlace"));
    }

    @Test
    public void testIsaImplementationTrue() {
        assertTrue(ClassComparator.isaImplementation(String.class, Serializable.class));
    }

    @Test
    public void testIsaImplementationFalse() {
        assertTrue(!ClassComparator.isaImplementation(String.class, Iterable.class));
    }
}
