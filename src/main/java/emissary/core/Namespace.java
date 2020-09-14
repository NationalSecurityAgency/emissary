/*
 * NameSpace.java
 *
 * Created on December 20, 2002, 10:48 AM
 */

package emissary.core;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used by Emissary core to manage named classes. Each registered place gets a name which includes the
 * host, port and place type. In this version of Namespace, we simply use a map of names to objects since all objects
 * will reside in one JVM.
 *
 * @author ce
 */
public class Namespace {
    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(Namespace.class);

    /** We will hold registerd class names in here */
    private static Map<String, Object> map = new ConcurrentHashMap<String, Object>();

    /**
     * Hide the creation a new instance of NameSpace
     */
    private Namespace() {}

    /**
     * Find a registered classname
     * 
     * @param arg the name of the registered item
     */
    public static Object lookup(final String arg) throws NamespaceException {

        Object obj = map.get(arg);
        logger.trace("Namespace.lookup({}) returning: {}", arg, obj);

        if (obj != null) {
            return obj;
        }

        for (final String key : map.keySet()) {
            if (key.endsWith("/" + arg)) {
                obj = map.get(key);
                break;
            }
        }

        if (obj == null) {
            throw new NamespaceException("Not found: " + arg);
        }
        return obj;
    }

    /**
     * Find a set of objects of a particular registered class
     *
     * @param arg the class of the registered item
     * @return a set of objects that are of the registered class
     */
    public static <T> Set<T> lookup(Class<T> arg) throws NamespaceException {
        return lookup(arg, false);
    }

    /**
     * Find a set of objects of a particular registered class
     *
     * @param arg the class of the registered item
     * @param silent true to silence the {@link NamespaceException}, false to throw
     * @return a set of objects that are of the registered class
     */
    public static <T> Set<T> lookup(Class<T> arg, boolean silent) throws NamespaceException {
        Set<T> lookups = Sets.newHashSet();
        map.values().stream().filter(arg::isInstance).forEach(o -> lookups.add((T) o));
        if (!silent && CollectionUtils.isEmpty(lookups)) {
            throw new NamespaceException("Not found: " + arg.getName());
        }
        return lookups;
    }

    /**
     * Test for existence of an object named by name
     * 
     * @param name name of object to look for
     * @return true if it exists
     */
    public static boolean exists(final String name) {
        if (map.containsKey(name)) {
            return true;
        }

        for (final String key : map.keySet()) {
            if (key.endsWith("/" + name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bind a new object into the namespace
     * 
     * @param arg the name of the object
     * @param arg2 the instance to bind
     */
    public static void bind(final String arg, final Object arg2) {
        logger.debug("Namespace.bind(" + arg + "," + arg2 + ")");
        map.put(arg, arg2);
    }

    /**
     * Remove a bound object
     * 
     * @param arg the name of the object that was used when it was bound
     */
    public static void unbind(final String arg) {
        logger.debug("Namespace.unbind(" + arg + ")");
        map.remove(arg);
    }

    /**
     * List places registered here return a copy to prevent concurrent modification errors
     * 
     * @return Set copy of the keys bound in this namespace
     */
    public static Set<String> keySet() {
        final Set<String> newSet = new TreeSet<String>();
        newSet.addAll(map.keySet());
        return newSet;
    }

    public static void dump() {
        System.out.println("dumping Namespace");
        for (String key : keySet()) {
            try {
                System.out.println("Key: " + key + " -> Value: " + lookup(key));
            } catch (NamespaceException e) {
                System.out.println("Couldn't find key: " + key);
            }
        }
    }

    public static void clear() {
        for (String key : keySet()) {
            unbind(key);
        }
    }
}
