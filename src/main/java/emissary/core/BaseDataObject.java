package emissary.core;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Class to hold data, header, footer, and attributes
 */
public class BaseDataObject extends ExtractedRecord implements IBaseDataObject {
    /**
     * The extracted records, if any
     */
    @Nullable
    protected List<IBaseDataObject> extractedRecords;

    /**
     * Create an empty BaseDataObject.
     */
    public BaseDataObject() {
        super();
    }

    /**
     * Create a new BaseDataObject with byte array and name passed in. WARNING: this implementation uses the passed in array
     * directly, no copy is made so the caller should not reuse the array.
     *
     * @param newData the bytes to hold
     * @param name the name of the data item
     */
    public BaseDataObject(final byte[] newData, final String name) {
        super(newData, name);
    }

    /**
     * Create a new BaseDataObject with byte array, name, and initial form WARNING: this implementation uses the passed in
     * array directly, no copy is made so the caller should not reuse the array.
     *
     * @param newData the bytes to hold
     * @param name the name of the data item
     * @param form the initial form of the data
     */
    public BaseDataObject(final byte[] newData, final String name, @Nullable final String form) {
        super(newData, name, form);
    }

    public BaseDataObject(final byte[] newData, final String name, final String form, @Nullable final String fileType) {
        super(newData, name, form, fileType);
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
