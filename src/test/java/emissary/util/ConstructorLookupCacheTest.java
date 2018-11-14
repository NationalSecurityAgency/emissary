package emissary.util;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Constructor;
import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.Test;

/**
 * Unit tests for {@link ConstructorLookupCache}.
 */
public class ConstructorLookupCacheTest extends UnitTest {

    // We need a known class and constructors to look up.
    private static final class ExampleClass {

        @SuppressWarnings("unused")
        public ExampleClass() {}

        @SuppressWarnings("unused")
        public ExampleClass(Integer arg1) {}

        @SuppressWarnings("unused")
        public ExampleClass(String arg1, String arg2, List<String> arg3) {}

        @SuppressWarnings("unused")
        public ExampleClass(int arg1, double arg2, boolean arg3, String arg4) {}

        /**
         * @return A constructor for this class that accepts the given arguments, or {@code null} if none.
         */
        public static Constructor<ExampleClass> getConstructor(Class<?>... args) {
            try {
                return ExampleClass.class.getConstructor(args);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }

    private static final Class<ExampleClass> EXAMPLE_CLASS = ExampleClass.class;

    private static final Class<?>[] ARGS_NONE = new Class<?>[] {};
    private static final Constructor<ExampleClass> EXAMPLE_CONSTRUCTOR_NONE = ExampleClass.getConstructor(ARGS_NONE);

    private static final Class<?>[] ARGS_INT = new Class<?>[] {Integer.class};
    private static final Constructor<ExampleClass> EXAMPLE_CONSTRUCTOR_INT = ExampleClass.getConstructor(ARGS_INT);

    private static final Class<?>[] ARGS_STRINGS = new Class<?>[] {String.class, String.class, List.class};
    private static final Constructor<ExampleClass> EXAMPLE_CONSTRUCTOR_STRINGS = ExampleClass.getConstructor(ARGS_STRINGS);

    private static final Class<?>[] ARGS_PRIM = new Class<?>[] {Integer.TYPE, Double.TYPE, Boolean.TYPE, String.class};
    private static final Constructor<ExampleClass> EXAMPLE_CONSTRUCTOR_PRIM = ExampleClass.getConstructor(ARGS_PRIM);

    /**
     * Call {@link ConstructorLookupCache#lookup} and ensure that it returns the given constructor.
     *
     * @param clazz The class of interest.
     * @param args The constructor arguments you want to use.
     * @param expectedConstructor The expected constructor object.
     */
    private static void assertLookup(final Class<?> clazz, final Class<?>[] args, final Constructor<?> expectedConstructor) {
        final Constructor<?> constructor = ConstructorLookupCache.lookup(clazz, args);
        assertEquals("lookup of " + clazz.getName() + " constructor should return a known constructor object", expectedConstructor, constructor);
    }

    @Test
    public void testThatNoArgLookupsWork() {
        assertLookup(EXAMPLE_CLASS, ARGS_NONE, EXAMPLE_CONSTRUCTOR_NONE);
    }

    @Test
    public void testThatSingleArgLookupsWork() {
        assertLookup(EXAMPLE_CLASS, ARGS_INT, EXAMPLE_CONSTRUCTOR_INT);
    }

    @Test
    public void testThatMultiArgLookupsWork() {
        assertLookup(EXAMPLE_CLASS, ARGS_STRINGS, EXAMPLE_CONSTRUCTOR_STRINGS);
    }

    @Test
    public void testThatPrimitiveArgLookupsWork() {
        assertLookup(EXAMPLE_CLASS, ARGS_PRIM, EXAMPLE_CONSTRUCTOR_PRIM);
    }

    /**
     * Do a constructor lookup and then ensure that it is cached. If we check the cache immediately after a successful
     * lookup, within the same thread, it is reasonable that we should find it cached.
     *
     * @param clazz The class of interest.
     * @param args The constructor arguments you want to use.
     * @param expectedConstructor The expected constructor object.
     */
    private static void assertCaching(final Class<?> clazz, final Class<?>[] args, final Constructor<?> expectedConstructor) {
        assertLookup(clazz, args, expectedConstructor);
        assertEquals("the recent lookup of the " + clazz.getName() + " constructor should be cached", expectedConstructor,
                ConstructorLookupCache.get(clazz, args));
    }

    @Test
    public void testThatRecentLookupsAreBeingCached() {
        assertCaching(EXAMPLE_CLASS, ARGS_NONE, EXAMPLE_CONSTRUCTOR_NONE);
        assertCaching(EXAMPLE_CLASS, ARGS_INT, EXAMPLE_CONSTRUCTOR_INT);
        assertCaching(EXAMPLE_CLASS, ARGS_STRINGS, EXAMPLE_CONSTRUCTOR_STRINGS);
        assertCaching(EXAMPLE_CLASS, ARGS_PRIM, EXAMPLE_CONSTRUCTOR_PRIM);
    }
}
