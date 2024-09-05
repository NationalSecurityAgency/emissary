package emissary.core;

import com.google.common.collect.LinkedListMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Class to hold data, header, footer, and attributes
 */
public class BaseDataObject extends BaseRecord implements Cloneable, IBaseDataObject {

    protected static final Logger logger = LoggerFactory.getLogger(BaseDataObject.class);

    /* Including this here make serialization of this object faster. */
    private static final long serialVersionUID = 7362181964652092657L;

    /**
     * The extracted records, if any
     */
    @Nullable
    protected List<IBaseDataObject> extractedRecords;

    public BaseDataObject() {
        super();
    }

    public BaseDataObject(final byte[] newData, final String name) {
        super(newData, name);
    }

    public BaseDataObject(final byte[] newData, final String name, @Nullable final String form) {
        super(newData, name, form);
    }

    public BaseDataObject(final byte[] newData, final String name, final String form, @Nullable final String fileType) {
        super(newData, name, form, fileType);
    }

    /**
     * Clone this payload
     */
    @Deprecated
    @Override
    public IBaseDataObject clone() throws CloneNotSupportedException {
        final BaseDataObject c = (BaseDataObject) super.clone();
        if ((this.theData != null) && (this.theData.length > 0)) {
            c.setData(this.theData, 0, this.theData.length);
        }

        if (this.seekableByteChannelFactory != null) {
            c.setChannelFactory(this.seekableByteChannelFactory);
        }

        c.currentForm = new ArrayList<>(this.currentForm);
        c.history = new TransformHistory(this.history);
        c.multipartAlternative = new HashMap<>(this.multipartAlternative);
        c.priority = this.priority;
        c.creationTimestamp = this.creationTimestamp;

        if ((this.extractedRecords != null) && !this.extractedRecords.isEmpty()) {
            c.clearExtractedRecords(); // remove super.clone copy
            for (final IBaseDataObject r : this.extractedRecords) {
                c.addExtractedRecord(r.clone());
            }
        }
        // This creates a deep copy Guava style
        c.parameters = LinkedListMultimap.create(this.parameters);

        return c;
    }

    @Override
    public List<IBaseDataObject> getExtractedRecords() {
        return this.extractedRecords;
    }

    @Override
    public void setExtractedRecords(final List<? extends IBaseDataObject> records) {
        if (records == null) {
            throw new IllegalArgumentException("Record list must not be null");
        }

        for (final IBaseDataObject r : records) {
            if (r == null) {
                throw new IllegalArgumentException("No added record may be null");
            }
        }

        this.extractedRecords = new ArrayList<>(records);
    }

    @Override
    public void addExtractedRecord(final IBaseDataObject record) {
        if (record == null) {
            throw new IllegalArgumentException("Added record must not be null");
        }

        if (this.extractedRecords == null) {
            this.extractedRecords = new ArrayList<>();
        }

        this.extractedRecords.add(record);
    }

    @Override
    public void addExtractedRecords(final List<? extends IBaseDataObject> records) {
        if (records == null) {
            throw new IllegalArgumentException("ExtractedRecord list must not be null");
        }

        for (final IBaseDataObject r : records) {
            if (r == null) {
                throw new IllegalArgumentException("No ExctractedRecord item may be null");
            }
        }

        if (this.extractedRecords == null) {
            this.extractedRecords = new ArrayList<>();
        }

        this.extractedRecords.addAll(records);
    }

    @Override
    public boolean hasExtractedRecords() {
        return (this.extractedRecords != null) && !this.extractedRecords.isEmpty();
    }

    @Override
    public void clearExtractedRecords() {
        this.extractedRecords = null;
    }

    @Override
    public int getExtractedRecordCount() {
        return (this.extractedRecords == null) ? 0 : this.extractedRecords.size();
    }
}
