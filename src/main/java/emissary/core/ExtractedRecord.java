package emissary.core;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class ExtractedRecord extends BaseDataObject {

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
    public List<IBaseDataObject> getExtractedRecords() {
        return null;
    }

    @Override
    public void setExtractedRecords(final List<? extends IBaseDataObject> extractedRecords) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addExtractedRecord(final IBaseDataObject extractedRecord) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addExtractedRecords(final List<? extends IBaseDataObject> extractedRecords) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasExtractedRecords() {
        return false;
    }

    @Override
    public void clearExtractedRecords() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getExtractedRecordCount() {
        return 0;
    }

}
