package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtractedRecordTest extends UnitTest {

    @Test
    void getExtractedRecords() {
        IBaseDataObject extract = new ExtractedRecord();
        assertNull(extract.getExtractedRecords());
    }

    @Test
    void setExtractedRecords() {
        IBaseDataObject extract = new ExtractedRecord();
        List<IBaseDataObject> extract2 = List.of(new ExtractedRecord());
        assertThrows(UnsupportedOperationException.class, () -> extract.setExtractedRecords(extract2));
    }

    @Test
    void addExtractedRecord() {
        IBaseDataObject extract = new ExtractedRecord();
        IBaseDataObject extract2 = new ExtractedRecord();
        assertThrows(UnsupportedOperationException.class, () -> extract.addExtractedRecord(extract2));
    }

    @Test
    void addExtractedRecords() {
        IBaseDataObject extract = new ExtractedRecord();
        List<IBaseDataObject> extract2 = List.of(new ExtractedRecord());
        assertThrows(UnsupportedOperationException.class, () -> extract.addExtractedRecords(extract2));
    }

    @Test
    void hasExtractedRecords() {
        IBaseDataObject extract = new ExtractedRecord();
        assertFalse(extract.hasExtractedRecords());
    }

    @Test
    void clearExtractedRecords() {
        IBaseDataObject extract = new ExtractedRecord();
        assertThrows(UnsupportedOperationException.class, extract::clearExtractedRecords);
    }

    @Test
    void getExtractedRecordCount() {
        IBaseDataObject extract = new ExtractedRecord();
        assertEquals(0, extract.getExtractedRecordCount());
    }
}
