package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtractedRecordTest extends UnitTest {

    @Test
    void getExtractedRecords() {
        ExtractedRecord extract = new ExtractedRecord();
        assertNull(extract.getExtractedRecords());
    }

    @Test
    void setExtractedRecords() {
        ExtractedRecord extract = new ExtractedRecord();
        List<ExtractedRecord> extract2 = List.of(new ExtractedRecord());
        assertThrows(UnsupportedOperationException.class, () -> extract.setExtractedRecords(extract2));
    }

    @Test
    void addExtractedRecord() {
        ExtractedRecord extract = new ExtractedRecord();
        ExtractedRecord extract2 = new ExtractedRecord();
        assertThrows(UnsupportedOperationException.class, () -> extract.addExtractedRecord(extract2));
    }

    @Test
    void addExtractedRecords() {
        ExtractedRecord extract = new ExtractedRecord();
        List<IBaseDataObject> extract2 = List.of(new ExtractedRecord());
        assertThrows(UnsupportedOperationException.class, () -> extract.addExtractedRecords(extract2));
    }

    @Test
    void hasExtractedRecords() {
        ExtractedRecord extract = new ExtractedRecord();
        assertFalse(extract.hasExtractedRecords());
    }

    @Test
    void clearExtractedRecords() {
        ExtractedRecord extract = new ExtractedRecord();
        assertThrows(UnsupportedOperationException.class, extract::clearExtractedRecords);
    }

    @Test
    void getExtractedRecordCount() {
        ExtractedRecord extract = new ExtractedRecord();
        assertEquals(0, extract.getExtractedRecordCount());
    }
}
