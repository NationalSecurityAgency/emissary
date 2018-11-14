package emissary.util;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for JMX operations.
 */
public class JMXUtil {

    private static final Logger logger = LoggerFactory.getLogger(JMXUtil.class);

    /**
     * Simple method (hopefully) to register an object with the JMXServer.
     *
     * @param obj object to register
     */
    public static void registerMBean(final Object obj) {
        registerMBean(obj, obj.getClass().getName());
    }

    public static void registerMBean(final Object obj, final String name) {
        if (Boolean.parseBoolean(System.getProperty("emissary.jmx.disabled"))) {
            logger.info("Emissary JMX is disabled via emissary.jmx.disabled property");
            return;
        }

        if (obj != null) {
            logger.debug("Regisering MBean for Class --> " + obj.getClass().getName());

            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            try {
                final ObjectName objName = new ObjectName("emissary:name=" + name);
                if (!mbs.isRegistered(objName)) {
                    mbs.registerMBean(obj, objName);
                }
            } catch (Exception e) {
                logger.error("Caught exception trying to register MBean for class {}", obj.getClass().getName(), e);
            }
        } else {
            logger.warn("Received request to register a null object");
        }
    }

    /** This class is not meant to be instantiated. */
    private JMXUtil() {}
}
