package emissary.core;

import java.io.IOException;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory implementation to provide an instance of whichever BaseDataObject implementation is configured for the system
 */
public class DataObjectFactory {

    public static final String DEFAULT_CLASS = BaseDataObject.class.getName();

    private static String CLAZZ;

    private static final Logger logger = LoggerFactory.getLogger(DataObjectFactory.class);

    /**
     * Initialize our implementation details
     */
    static {
        try {
            final Configurator c = ConfigUtil.getConfigInfo(emissary.pool.AgentPool.class);
            CLAZZ = c.findStringEntry("payload.class", DEFAULT_CLASS);
        } catch (IOException ioe) {
            logger.warn("Unable to configure DataObjectFactory", ioe);
            CLAZZ = DEFAULT_CLASS;
        }
    }

    /**
     * Take away public constructor
     */
    private DataObjectFactory() {
        // Nothing to do.
    }

    /**
     * Override implementation details
     */
    public static void setImplementingClass(final String clazz) {
        CLAZZ = clazz;
    }

    /**
     * Get the name of the impl we are using
     */
    public static String getImplementingClass() {
        return CLAZZ;
    }

    /**
     * Get an instance of the configured DataObject impl
     */
    public static IBaseDataObject getInstance() {
        final Object o = Factory.create(CLAZZ);
        return (IBaseDataObject) o;
    }

    /**
     * Get an instance of the configured DataObject impl with pretty much arbitrary arguments to the constructor
     * 
     * @param args the arguments to the BaseDataObject constructor
     */
    public static IBaseDataObject getInstance(final Object... args) {
        final Object o = Factory.create(CLAZZ, args);
        return (IBaseDataObject) o;
    }

    /**
     * Get an instance of the configured DataObject impl with filename, form, and file type set
     * 
     * @param payload the payload data
     * @param filename the filename
     * @param fileTypeAndForm the form and filetype to set on the IBDO
     * @return an IBDO with the payload, filename, set with the file type and form set to the same value
     */
    public static IBaseDataObject getInstance(final byte[] payload, final String filename, final String fileTypeAndForm) {
        return getInstance(payload, filename, fileTypeAndForm, fileTypeAndForm);
    }

    /**
     * Get an instance of the configured DataObject impl with filename, form, and file type set
     * 
     * @param payload the payload data
     * @param filename the filename
     * @param form the form to set on the IBDO
     * @param fileType the file type to set on the IBDO
     * @return an IBDO with the payload, filename, file type, and form set
     */
    public static IBaseDataObject getInstance(final byte[] payload, final String filename, final String form, final String fileType) {
        final Object o = Factory.create(CLAZZ, payload, filename, form, fileType);
        return (IBaseDataObject) o;
    }
}
