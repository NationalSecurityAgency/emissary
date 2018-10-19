/*
 * Factory.java
 *
 * Created on December 20, 2002, 10:43 AM
 */

package emissary.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import emissary.util.ClassLookupCache;
import emissary.util.ConstructorLookupCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory.create() is one of the main methods that Emissary uses. This method simply constructs objects in the server
 * name space so they may be referred to. Since this implementation is intended to run on a single machine, the create()
 * method simply uses reflection (i.e. Class.forname() and Constructor.newInstance) to create the specified object.
 *
 * In the cases where a name (or handle) is supplied with the constructor arguments, the Namespace.bind method is called
 * to save a reference to the object with that name.
 *
 * @see emissary.core.Namespace#bind
 * @author ce
 */
public class Factory {
    public static final boolean debug = false;

    private static final Logger logger = LoggerFactory.getLogger(Factory.class);

    /**
     * Take away the public constructor
     */
    private Factory() {}

    /**
     * Create an object from it's classname using args for arguments
     * 
     * @param className the string classname to get a new instance of
     * @param args the arguments to a public constructor of classname
     * @return The newly instantiated object. If it cannot instantiate, this throws some sort of Exception/Error.
     */
    public static Object create(final String className, final Object... args) {
        logger.debug("Factory.create1({}, {})", className, Arrays.toString(args));
        try {
            final Class<?> clazz = ClassLookupCache.lookup(className);
            final List<Class<?>> types = new ArrayList<>();
            for (Object o : args) {
                if (o == null) {
                    types.add(null);
                } else {
                    types.add(o.getClass());
                }
            }
            logger.debug("checking:" + types);

            final Constructor<?> constructor = ConstructorLookupCache.lookup(clazz, types.toArray(new Class[0]));
            if (constructor == null) {
                logger.info("Failed to find constructor for args({}) types ({}) : {}", args.length, types.size(), types);
                throw new Error("failed to find suitable constructor for class " + className);
            } else {
                return constructor.newInstance(args);
            }
        } catch (ClassNotFoundException e1) {
            logger.error("Could not find class", e1);
            throw new Error(e1);
        } catch (InstantiationException e3) {
            logger.error("Could not instantiate", e3);
            throw new Error(e3);
        } catch (IllegalAccessException e4) {
            logger.error("Could not call constructor", e4);
            throw new Error(e4);
        } catch (InvocationTargetException e5) {
            logger.error("Constructor failed", e5);
            throw new Error(e5);
        } catch (Throwable t) {
            logger.error("Problem in factory", t);
            throw new Error(t);
        }
    }

    /**
     * Create an object of the type specified using a no-arg constructor
     * 
     * @param className the string class name to instantiate
     * @return The newly instantiated object. If it cannot instantiate, this throws some sort of Exception/Error.
     */
    public static Object create(final String className) {
        try {
            // Since we don't have to pass any arguments to the
            // constructor, we can try the simple approach of looking
            // up the class and invoking its no-arg constructor
            // directly. When this succeeds, it can avoid the
            // overhead of calling getConstructor() on the Class
            // object.
            return ClassLookupCache.lookup(className).newInstance();
        } catch (Throwable e) {
            // The simple approach failed, so we'll fall back on a
            // more complicated approach that probably has more
            // overhead but also has better error reporting.
            return create(className, new Object[] {});
        }
    }

    /**
     * Create an object and bind it into the namespace
     * 
     * @param className the string classname to get a new instance of
     * @param args the arguments to a public constructor of classname
     * @param location name used to bind into the namespace
     * @return the newly instantiated object
     */
    public static Object create(final String className, final Object[] args, final String location) {
        if (logger.isDebugEnabled()) {
            logger.debug("Factory.create(" + className + "," + Arrays.toString(args) + "," + location + ")");
        }
        final Object o = create(className, args);
        Namespace.bind(location, o);
        return o;
    }

    /**
     * Create an object and bind it into the namespace. This method is used to prevent the ambiguity around overloaded
     * varargs methods.
     * 
     * @param className the string classname to get a new instance of
     * @param location name used to bind into the namespace
     * @param args the arguments to a public constructor of classname
     * @return the newly instantiated object
     */
    public static Object createV(final String className, final String location, final Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Factory.create(" + className + "," + location + "," + Arrays.toString(args) + ")");
        }
        final Object o = create(className, args);
        Namespace.bind(location, o);
        return o;
    }

}
