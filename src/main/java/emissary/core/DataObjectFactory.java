package emissary.core;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.pool.AgentPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Factory implementation to provide an instance of whichever BaseDataObject implementation is configured for the system
 */
public class DataObjectFactory {

    private static final Logger logger = LoggerFactory.getLogger(DataObjectFactory.class);

    public static final String DEFAULT_CLASS = BaseDataObject.class.getName();

    private static String clazz;

    /*
     * Initialize our implementation details
     */
    static {
        try {
            final Configurator c = ConfigUtil.getConfigInfo(AgentPool.class);
            clazz = c.findStringEntry("payload.class", DEFAULT_CLASS);
        } catch (IOException ioe) {
            logger.warn("Unable to configure DataObjectFactory", ioe);
            clazz = DEFAULT_CLASS;
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
     *
     * @param clazz {@link IBaseDataObject} implementation
     */
    public static void setImplementingClass(final String clazz) {
        DataObjectFactory.clazz = clazz;
    }

    /**
     * Get the name of the impl we are using
     *
     * @return {@link IBaseDataObject} implementation
     */
    public static String getImplementingClass() {
        return clazz;
    }

    /**
     * Get an instance of the configured DataObject impl
     *
     * @return {@link IBaseDataObject} implementation
     */
    public static IBaseDataObject getInstance() {
        final Object o = Factory.create(clazz);
        return (IBaseDataObject) o;
    }

    /**
     * Get an instance of the configured DataObject impl
     *
     * @param isExtracted true to return extracted record implementation, false otherwise
     * @return {@link IBaseDataObject} implementation
     */
    public static IBaseDataObject getInstance(boolean isExtracted) {
        final Object o = Factory.create(clazz, isExtracted);
        return (IBaseDataObject) o;
    }

    /**
     * Get an instance of the configured DataObject impl with pretty much arbitrary arguments to the constructor
     * 
     * @param args the arguments to the BaseDataObject constructor
     * @return {@link IBaseDataObject} implementation
     */
    public static IBaseDataObject getInstance(final Object... args) {
        final Object o = Factory.create(clazz, args);
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
        return getInstance(payload, filename, fileTypeAndForm, false);
    }

    /**
     * Get an instance of the configured DataObject impl with filename, form, and file type set
     *
     * @param payload the payload data
     * @param filename the filename
     * @param fileTypeAndForm the form and filetype to set on the IBDO
     * @param isExtracted true to return extracted record implementation, false otherwise
     * @return an IBDO with the payload, filename, set with the file type and form set to the same value
     */
    public static IBaseDataObject getInstance(final byte[] payload, final String filename, final String fileTypeAndForm, boolean isExtracted) {
        return getInstance(payload, filename, fileTypeAndForm, fileTypeAndForm, isExtracted);
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
        return getInstance(payload, filename, form, fileType, false);
    }

    /**
     * Get an instance of the configured DataObject impl with filename, form, and file type set
     *
     * @param payload the payload data
     * @param filename the filename
     * @param form the form to set on the IBDO
     * @param fileType the file type to set on the IBDO
     * @param isExtracted true to return extracted record implementation, false otherwise
     * @return an IBDO with the payload, filename, file type, and form set
     */
    public static IBaseDataObject getInstance(final byte[] payload, final String filename, final String form, final String fileType,
            boolean isExtracted) {
        final Object o = Factory.create(clazz, payload, filename, form, fileType, isExtracted);
        return (IBaseDataObject) o;
    }
}
