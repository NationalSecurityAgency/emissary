package emissary.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassComparator {

    private static final Logger logger = LoggerFactory.getLogger(ClassComparator.class);

    /** This class is not meant to be instantiated. */
    private ClassComparator() {}

    public static void main(final String[] args) {

        if (args.length < 2) {
            System.err.println("usage: java emissary.Util class1 class2");
            return;
        }

        if (ClassComparator.isa(args[0], args[1])) {
            System.err.println(args[1] + " isa " + args[0]);
        }
    }

    public static boolean isa(final String one, final String two) {
        String currentTwo = two;
        try {
            while (currentTwo != null) {
                logger.debug("Check " + one + " and " + currentTwo);
                if (one.equals(currentTwo)) {
                    return true;
                }
                final Class<?> c = Class.forName(currentTwo).getSuperclass();
                if (c == null) {
                    logger.debug("Out with a null superclass from two " + currentTwo);
                    break;
                }
                currentTwo = c.getName();
            }
        } catch (Exception ex) {
            logger.debug("one={}, two={}", one, currentTwo, ex);
        }
        return false;
    }

    public static boolean isaImplementation(Class<? extends Object> clazz, Class<? extends Object> iface) {
        return iface.isAssignableFrom(clazz);
    }
}
