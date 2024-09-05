package emissary.core;

import javax.annotation.Nullable;

public class ExtractedRecord extends BaseRecord implements IExtractedRecord {

    private static final long serialVersionUID = -1298573504297726742L;

    public ExtractedRecord() {
        super();
    }

    public ExtractedRecord(byte[] newData, String name) {
        super(newData, name);
    }

    @SuppressWarnings("unused")
    public ExtractedRecord(byte[] newData, String name, @Nullable String form) {
        super(newData, name, form);
    }

    @SuppressWarnings("unused")
    public ExtractedRecord(byte[] newData, String name, String form, @Nullable String fileType) {
        super(newData, name, form, fileType);
    }

}
