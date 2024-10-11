package emissary.core;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class ExtractedRecord extends BaseDataObject implements IExtractedRecord {

    private static final long serialVersionUID = -6716572014928830135L;

    public ExtractedRecord() {
        super();
        configure();
    }

    public ExtractedRecord(byte[] newData, String name) {
        super(newData, name);
        configure();
    }

    @SuppressWarnings("unused")
    public ExtractedRecord(byte[] newData, String name, @Nullable String form) {
        super(newData, name, form);
        configure();
    }

    @SuppressWarnings("unused")
    public ExtractedRecord(byte[] newData, String name, String form, @Nullable String fileType) {
        super(newData, name, form, fileType);
        configure();
    }

    private void configure() {
        this.extractedRecords = Collections.emptyList();
    }

    @Override
    @Deprecated(forRemoval = true)
    @Nullable
    public List<IBaseDataObject> getExtractedRecords() {
        return null;
    }

    @Override
    @Deprecated(forRemoval = true)
    public void setExtractedRecords(final List<? extends IBaseDataObject> extractedRecords) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated(forRemoval = true)
    public void addExtractedRecord(final IBaseDataObject extractedRecord) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated(forRemoval = true)
    public void addExtractedRecords(final List<? extends IBaseDataObject> extractedRecords) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean hasExtractedRecords() {
        return false;
    }

    @Override
    @Deprecated(forRemoval = true)
    public void clearExtractedRecords() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated(forRemoval = true)
    public int getExtractedRecordCount() {
        return 0;
    }

}
