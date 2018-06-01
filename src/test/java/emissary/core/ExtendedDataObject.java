package emissary.core;

import java.io.Serializable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class exists to make sure the BaseDataObject can be extended properly and used from JNIPlace and JNIMultiPlace
 */
public class ExtendedDataObject extends BaseDataObject implements Serializable, IExtendedDataObject {

    /**
     * provide uid for serialization
     */
    private static final long serialVersionUID = -1487131411076568497L;

    private static final Logger logger = LoggerFactory.getLogger(ExtendedDataObject.class);

    protected String[] NEW_FILETYPE_EMPTY = {"FOO", "BAR", "QUUX", "UNKNOWN"};

    protected int intVar = -1;
    protected long longVar = 100L;
    protected String stringVar = "A string var";


    public ExtendedDataObject() {
        super();
        this.theData = null;
        FILETYPE_EMPTY = this.NEW_FILETYPE_EMPTY;
        this.intVar = 37;
    }

    public ExtendedDataObject(final byte[] newData, final String name) {
        super();
        setData(newData);
        setFilename(name);
        this.intVar = 38;
    }

    public ExtendedDataObject(final byte[] newData, final String name, final String form) {
        this(newData, name);
        if (form != null) {
            pushCurrentForm(form);
        }
        this.intVar = 39;
    }

    @Override
    public boolean isExtended() {
        return true;
    }

    @Override
    public void setCurrentForm(final String s) {
        logger.debug("Setting current form to " + s);
        super.setCurrentForm(s);
    }

    @Override
    public void setFilename(final String s) {
        logger.debug("Setting filename to " + s);
        super.setFilename(s);
    }

    @Override
    public void setData(final byte[] b) {
        logger.debug("Setting data with " + (b == null ? 0 : b.length) + " bytes");
        super.setData(b);
    }

    @Override
    public void setParameter(final String key, final Object val) {
        deleteParameter(key);
        putParameter(key, val);
    }

    @Override
    public void setParameters(final Map<? extends String, ? extends Object> k) {
        putParameters(k);
    }
}
