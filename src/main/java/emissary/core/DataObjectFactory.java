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
    public static final String DEFAULT_EXTRACT_CLASS = ExtractedRecord.class.getName();

    @SuppressWarnings("NonFinalStaticField")
    private static String clazz;
    @SuppressWarnings("NonFinalStaticField")
    private static String extractedClazz;

    /*
     * Initialize our implementation details
     */
    static {
        try {
            final Configurator c = ConfigUtil.getConfigInfo(AgentPool.class);
            clazz = c.findStringEntry("payload.class", DEFAULT_CLASS);
            extractedClazz = c.findStringEntry("payload.extracted.class", DEFAULT_EXTRACT_CLASS);
        } catch (IOException ioe) {
            logger.warn("Unable to configure DataObjectFactory", ioe);
            clazz = DEFAULT_CLASS;
            extractedClazz = DEFAULT_EXTRACT_CLASS;
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
     * Override implementation details
     *
     * @param clazz {@link IBaseDataObject} implementation
     */
    public static void setImplementingExtractClass(final String clazz) {
        DataObjectFactory.extractedClazz = clazz;
    }

    /**
     * Get the name of the impl we are using
     *
     * @return {@link IBaseDataObject} implementation
     */
    public static String getImplementingExtractClass() {
        return extractedClazz;
    }

    /* IBaseDataObject */

    /**
     * Get an instance of the configured DataObject impl
     */
    public static IBaseDataObject getInstance() {
        final Object o = Factory.create(clazz);
        return (IBaseDataObject) o;
    }

    /**
     * Get an instance of the configured DataObject impl with pretty much arbitrary arguments to the constructor
     *
     * @param args the arguments to the BaseDataObject constructor
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
        return getInstance(payload, filename, fileTypeAndForm, fileTypeAndForm);
    }

    public static IBaseDataObject getInstance(final byte[] payload, final String filename, final String fileTypeAndForm, IBaseDataObject tld) {
        final Object o = Factory.create(clazz, payload, filename, fileTypeAndForm, tld);
        return (IBaseDataObject) o;
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
        final Object o = Factory.create(clazz, payload, filename, form, fileType);
        return (IBaseDataObject) o;
    }

    public static IBaseDataObject getInstance(final byte[] payload, final String filename, final String form, final String fileType,
            IBaseDataObject tld) {
        final Object o = Factory.create(clazz, payload, filename, form, fileType, tld);
        return (IBaseDataObject) o;
    }

    /* IExtractedRecord */

    /**
     * Get an instance of the configured ExtractedObject impl
     */
    public static IExtractedRecord getExtractInstance() {
        final Object o = Factory.create(extractedClazz);
        return (IExtractedRecord) o;
    }

    /**
     * Get an instance of the configured ExtractedObject impl with pretty much arbitrary arguments to the constructor
     *
     * @param args the arguments to the IExtractedRecord constructor
     */
    public static IExtractedRecord getExtractInstance(final Object... args) {
        final Object o = Factory.create(extractedClazz, args);
        return (IExtractedRecord) o;
    }

    /**
     * Get an instance of the configured ExtractedObject impl with filename, form, and file type set
     *
     * @param payload the payload data
     * @param filename the filename
     * @param fileTypeAndForm the form and filetype to set on the IBDO
     * @return an IBDO with the payload, filename, set with the file type and form set to the same value
     */
    public static IExtractedRecord getExtractInstance(final byte[] payload, final String filename, final String fileTypeAndForm) {
        return getExtractInstance(payload, filename, fileTypeAndForm, fileTypeAndForm);
    }

    /**
     * Get an instance of the configured ExtractedObject impl with filename, form, and file type set
     *
     * @param payload the payload data
     * @param filename the filename
     * @param form the form to set on the IBDO
     * @param fileType the file type to set on the IBDO
     * @return an IBDO with the payload, filename, file type, and form set
     */
    public static IExtractedRecord getExtractInstance(final byte[] payload, final String filename, final String form, final String fileType) {
        final Object o = Factory.create(extractedClazz, payload, filename, form, fileType);
        return (IExtractedRecord) o;
    }
}
