package emissary.util;

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implements a simple caching mechanism for {@link Constructor} lookups. For example if the same class constructor
 * is looked up repeatedly, using this cache may be able to avoid a lot of JVM reflection overhead.
 *
 * <p>
 * Note that the cache implementation may have a small capacity and/or be thread-specific, so storing something in the
 * cache does not <i>guarantee</i> that it will be indefinitely cached or that the cached value will be visible to other
 * threads.
 */
public final class ConstructorLookupCache {

    private static final Logger logger = LoggerFactory.getLogger(ConstructorLookupCache.class);

    /**
     * Represents a known result for looking up a class constructor that can handle a specific set of argument types.
     */
    private static final class KnownConstructor {

        /** The class being constructed. */
        private final Class<?> clazz;

        /** The argument types that will be passed to the constructor. */
        private final Class<?>[] argTypes;

        /**
         * A constructor for {@link #clazz} that accepts the argument types from {@link #argTypes}.
         */
        private final Constructor<?> constructor;

        /**
         * Create a binding between a class, some constructor argument types, and a constructor.
         *
         * @param clazz The class being constructed.
         * @param argTypes The argument types that will be passed to the constructor.
         * @param constructor A constructor for {@code clazz} that can accept the argument types specified in {@code argTypes}.
         */
        public KnownConstructor(final Class<?> clazz, final Class<?>[] argTypes, final Constructor<?> constructor) {
            this.clazz = clazz;
            this.argTypes = argTypes;
            this.constructor = constructor;
        }

        /**
         * Get the stored constructor if it matches the given class and argument types.
         *
         * @param desiredClazz The type of class being constructed.
         * @param desiredArgTypes The argument types that will be passed to the constructor.
         * @return If the class and argument types match the ones that are stored, this returns the stored constructor that is
         *         assumed to be compatible with those argument types and create an object of the given type; otherwise
         *         {@code null}.
         */
        public Constructor<?> getConstructor(final Class<?> desiredClazz, final Class<?>[] desiredArgTypes) {
            // The stored class has to match the desired class.
            if (!this.clazz.equals(desiredClazz)) {
                return null; // Non-matching class.
            }

            // The stored argument count has to match the desired
            // argument count.
            final Class<?>[] myArgTypes = this.argTypes;
            if (myArgTypes.length != desiredArgTypes.length) {
                return null; // The number of arguments doesn't match.
            }

            // The stored argument types have to match the desired
            // argument types.
            for (int i = 0; i < myArgTypes.length; i++) {
                if (myArgTypes[i] == null) {
                    if (desiredArgTypes[i] != null) {
                        return null; // Non-matching null argument type.
                    }
                } else if (!myArgTypes[i].equals(desiredArgTypes[i])) {
                    return null; // Non-matching non-null argument type.
                }
            }

            // At this point we know that we have the desired class
            // and arguments.
            return this.constructor;
        }
    }

    /**
     * A cached constructor lookup result. If a thread is asked to invoke the same constructor repeatedly, we can cache the
     * result of the lookup to avoid some costly reflection calls.
     */
    private static final ThreadLocal<SoftReference<KnownConstructor>> cachedConstructorLookup = new ThreadLocal<SoftReference<KnownConstructor>>();

    /** A table mapping boxed classes to their primitive types. */
    private static final Map<Class<?>, Class<?>> PrimClass = new HashMap<Class<?>, Class<?>>();

    // Initialize the mappings.
    static {
        PrimClass.put(Integer.class, Integer.TYPE);
        PrimClass.put(Boolean.class, Boolean.TYPE);
        PrimClass.put(Float.class, Float.TYPE);
        PrimClass.put(Character.class, Character.TYPE);
        PrimClass.put(Long.class, Long.TYPE);
        PrimClass.put(Double.class, Double.TYPE);
        PrimClass.put(Byte.class, Byte.TYPE);
    }

    /**
     * Convert a boxed type into its primitive type.
     * 
     * @param clazz The type of interest.
     * @return If {@code clazz} is a boxed primitive, return the primitive type; otherwise just return {@code clazz}.
     */
    private static Class<?> getPrim(final Class<?> clazz) {
        final Class<?> prim = PrimClass.get(clazz);
        return (prim != null) ? prim : clazz;
    }

    /**
     * Look for a constructor for the given class type which can accept the given argument types, without using the lookup
     * cache.
     *
     * @param clazz The type of object that will be constructed.
     * @param argTypes The types of the arguments that will be passed to the class constructor.
     * @return If {@code clazz} has a constructor that can accept the argument types in {@code argTypes}, this returns the
     *         matching constructor; otherwise {@code null}.
     */
    private static Constructor<?> directConstructorLookup(final Class<?> clazz, final Class<?>[] argTypes) {
        // Look for an exact match.
        try {
            return clazz.getConstructor(argTypes);
        } catch (NoSuchMethodException e) {
            logger.debug("No constructor for [" + clazz.getName() + "] in Factory.create())");
        }

        // There was no exact match, so look through the existing
        // constructors for an assignable match.
        NEXT_CANDIDATE_CONSTRUCTOR: for (final Constructor<?> candidate : clazz.getConstructors()) {
            final Class<?>[] ctypes = candidate.getParameterTypes();

            if (logger.isDebugEnabled()) {
                logger.debug("Checking:" + clazz.getName() + ", " + clazz);
                logger.debug("   types   :" + Arrays.toString(ctypes));
                logger.debug("   numParms:" + ctypes.length + " =? " + argTypes.length);
            }

            // If the candidate constructor doesn't have the same
            // number of arguments, it definitely isn't compatible
            // with the desired argument types.
            if (ctypes.length != argTypes.length) {
                logger.debug("    not equal:");
                continue NEXT_CANDIDATE_CONSTRUCTOR;
            }

            // The candidate takes the right number of arguments, so
            // compare its expected types to the types that will be
            // passed. If the given type is not assignable to the
            // expected type, then this constructor is not compatible.
            for (int j = 0; j < argTypes.length; j++) {
                final Class<?> a = ctypes[j];
                final Class<?> b = argTypes[j];

                if ((a != null) && (b != null)) {
                    if (a.isAssignableFrom(b)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("   param=" + a + "  assignable " + b);
                        }
                    } else {
                        final Class<?> bPrim = getPrim(b);
                        if (a.isAssignableFrom(bPrim)) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("   param:" + a + "  assignable " + b);
                            }
                        } else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("   param:" + a + " !assignable " + b);
                            }
                            continue NEXT_CANDIDATE_CONSTRUCTOR;
                        }
                    }
                }
            }

            // If we reach here, the current candidate constructor is
            // a compatible match.
            return candidate;
        }

        // None of the class's available constructors are compatible
        // with the given argument types.
        return null;
    }

    /**
     * Look for a constructor in the cache.
     *
     * @param clazz The class to be constructed.
     * @param argTypes The argument types that the caller intends to pass to the constructor.
     * @return If a matching constructor is in the cache, return it. Otherwise {@code null}.
     */
    public static Constructor<?> get(final Class<?> clazz, final Class<?>[] argTypes) {
        // Currently there is at most one cached lookup per thread, so
        // we can just check if the current thread knows about the
        // given class and constructor arguments.
        final SoftReference<KnownConstructor> softResult = cachedConstructorLookup.get();
        if (softResult == null) {
            return null; // Nothing is currently cached in this thread.
        }

        final KnownConstructor knownResult = softResult.get();
        if (knownResult == null) {
            return null; // There was something cached but it's been lost.
        }

        // We do have a cached lookup. It can be used iff it matches the
        // given class and constructor arguments.
        return knownResult.getConstructor(clazz, argTypes);
    }

    /**
     * Store a constructor lookup in the cache.
     *
     * @param clazz The class to be constructed.
     * @param argTypes The argument types that the caller intends to pass to the constructor.
     * @param constructor A constructor for {@code clazz} that can accept the argument types specified in {@code argTypes}.
     */
    public static void put(final Class<?> clazz, final Class<?>[] argTypes, final Constructor<?> constructor) {
        cachedConstructorLookup.set(new SoftReference<KnownConstructor>(new KnownConstructor(clazz, argTypes, constructor)));
    }

    /**
     * Look for a constructor for the given class type which can accept the given argument types.
     *
     * @param clazz The class to be constructed.
     * @param argTypes The argument types that the caller intends to pass to the constructor.
     * @return A matching constructor for the specified class, or {@code null} if no such constructor was found.
     */
    public static Constructor<?> lookup(final Class<?> clazz, final Class<?>[] argTypes) {
        final Constructor<?> cachedConstructor = get(clazz, argTypes);
        if (cachedConstructor != null) {
            // We found the constructor in the cache.
            return cachedConstructor;
        } else {
            // The desired constructor is not currently cached, so
            // look it up directly.
            final Constructor<?> uncachedConstructor = directConstructorLookup(clazz, argTypes);

            // If we got a result, cache it before returning.
            if (uncachedConstructor != null) {
                put(clazz, argTypes, uncachedConstructor);
            }
            return uncachedConstructor;
        }
    }

    /** This is a static utility class, so prevent instantiation. */
    private ConstructorLookupCache() {}
}
