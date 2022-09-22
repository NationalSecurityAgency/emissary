package emissary.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClassLookupCache}.
 */
class ClassLookupCacheTest extends UnitTest {

    // We just need some class name to look up and its corresponding
    // class object.
    private static final Class<?> TEST_CLASS_OBJECT = ClassLookupCacheTest.class;
    private static final String TEST_CLASS_NAME = TEST_CLASS_OBJECT.getName();

    /**
     * Call {@link ClassLookupCache#lookup(String)} and ensure that it returns the given class.
     *
     * @param className The class name to look up.
     * @param expectedClazz The expected class object.
     */
    private static void assertLookup(final String className, final Class<?> expectedClazz) {
        try {
            final Class<?> clazz = ClassLookupCache.lookup(className);
            assertEquals(expectedClazz, clazz, "lookup of " + className + " should return known class object");
        } catch (ClassNotFoundException e) {
            fail("could not find the class " + className, e);
        }
    }

    @Test
    void testThatLookupsWork() {
        // Just make sure that lookup(name) is working at all.
        assertLookup(TEST_CLASS_NAME, TEST_CLASS_OBJECT);
    }

    @Test
    void testThatRecentLookupsAreBeingCached() {
        // Do a regular lookup.
        assertLookup(TEST_CLASS_NAME, TEST_CLASS_OBJECT);

        // Now make sure the lookup got cached. Since we're on the
        // same thread and we know the same class was just looked up
        // successfully and very little time has elapsed, it's a
        // reasonable expectation that the class is still cached.
        assertEquals(TEST_CLASS_OBJECT,
                ClassLookupCache.get(TEST_CLASS_NAME),
                "the recent lookup of " + TEST_CLASS_NAME + " should cache the class object");
    }
}
