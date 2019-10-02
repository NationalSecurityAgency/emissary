package emissary.util;

import java.lang.ref.SoftReference;

/**
 * This implements a simple caching mechanism for {@link Class#forName(String)}. For example if the same class name is
 * looked up repeatedly, using this cache may be able to avoid a lot of JVM reflection overhead.
 *
 * <p>
 * To use this, just call {@link ClassLookupCache#lookup(String)} where you would normally use
 * {@link Class#forName(String)}. There are also methods for directly manipulating the cache but most uses can avoid
 * those.
 *
 * <p>
 * Note that the cache implementation may have a small capacity and/or be thread-specific, so storing something in the
 * cache does not <i>guarantee</i> that it will be indefinitely cached or that the cached value will be visible to other
 * threads.
 */
public final class ClassLookupCache {

    /**
     * A binding from a name string to a {@link Class} object.
     *
     * @param <T> The object type that will be produced by the class.
     */
    private static final class NamedClass<T> {

        /** The class name. */
        private final String className;

        /** A class object that matches {@link #className}. */
        private final Class<T> clazz;

        /**
         * Create a new binding between a class name and a matching object.
         *
         * @param className the class name.
         * @param clazz A class object that matches {@code className}.
         */
        private NamedClass(final String className, final Class<T> clazz) {
            this.className = className;
            this.clazz = clazz;
        }

        /**
         * Get the class object, if it has the given name.
         *
         * @param desiredName The class name to check.
         * @return If this instance has the given {@code desiredName}, this returns its {@link Class} object. Otherwise
         *         {@code null}.
         */
        public Class<T> getClass(final String desiredName) {
            return this.className.equals(desiredName) ? this.clazz : null;
        }

        /**
         * Get a binding between a class name and a matching object.
         *
         * <p>
         * The reason we use a static builder method is that it can handle wildcards such as {@code <?>} more easily than when
         * calling the constructor directly.
         *
         * @param className the class name.
         * @param clazz A class object that matches {@code className}.
         * @return A binding between the given name and class object.
         */
        public static <T> NamedClass<T> getInstance(final String className, final Class<T> clazz) {
            return new NamedClass<T>(className, clazz);
        }
    }

    /**
     * A cached class object. If a thread is asked to repeatedly construct the same type of object, we can cache the class
     * name lookup so that subsequent constructions can get to the {@link Class} object without doing a full lookup in the
     * JVM.
     */
    private static final ThreadLocal<SoftReference<NamedClass<?>>> cachedLookupResult = new ThreadLocal<SoftReference<NamedClass<?>>>();

    /**
     * Look up a class in the cache.
     *
     * @param className The class name to find.
     * @return If the class name is currently known to the cache, the corresponding {@link Class} object is returned.
     *         Otherwise {@code null}.
     */
    public static Class<?> get(final String className) {
        // Currently there is at most one cached lookup per thread, so
        // we can just check if the current thread knows about the
        // given class name.
        final SoftReference<NamedClass<?>> softResult = cachedLookupResult.get();
        if (softResult == null) {
            return null; // Nothing is currently cached in this thread.
        }

        final NamedClass<?> actualResult = softResult.get();
        if (actualResult == null) {
            return null; // There was something cached but it's been lost.
        }

        // We do have a cached lookup. It can be used iff it matches
        // the given name.
        return actualResult.getClass(className);
    }

    /**
     * Store a class lookup in the cache.
     *
     * @param className The class name.
     * @param clazz The class. Assumed to match {@code className}.
     */
    public static void put(final String className, final Class<?> clazz) {
        cachedLookupResult.set(new SoftReference<NamedClass<?>>(NamedClass.getInstance(className, clazz)));
    }

    /**
     * Look up a class by name. This is basically a utility method that can be called instead of
     * {@link Class#forName(String)}, and will try to use the cache to speed up the lookups.
     *
     * @param className The class name to get.
     * @return The {@link Class} object corresponding to {@code className}.
     * @throws ClassNotFoundException If the class name could not be resolved.
     */
    public static Class<?> lookup(final String className) throws ClassNotFoundException {
        final Class<?> cachedResult = get(className);
        if (cachedResult != null) {
            // We found the class in the cache.
            return cachedResult;
        } else {
            // The given class name is not currently cached, so look
            // it up directly.
            final Class<?> uncachedResult = Class.forName(className);

            // If we reach here, the class was found. Cache the
            // result before returning.
            put(className, uncachedResult);
            return uncachedResult;
        }
    }

    /** This is a static utility class, so prevent instantiation. */
    private ClassLookupCache() {}
}
