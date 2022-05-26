package emissary.id;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SizeIdPlaceTest extends UnitTest {

    SizeIdPlace place;

    @Override
    @BeforeEach
    public void setUp() throws IOException {

        place = new SizeIdPlace();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
    }

    @Test
    void testSizes() {
        assertEquals("SIZE_SMALL", place.fileTypeBySize(2500), "Size label chosen from size");
    }

    @Test
    void testZero() {
        assertEquals("SIZE_ZERO", place.fileTypeBySize(0), "Size zero chosen");
    }

    @Test
    void testUpperBound() {
        assertEquals("SIZE_ASTRONOMICAL", place.fileTypeBySize(Integer.MAX_VALUE), "Size on upper bound");
    }

    @Test
    void testPayload() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        place.process(payload);
        assertEquals("SIZE_TINY", payload.currentForm(), "Current form set from size");
        assertEquals("SIZE_TINY", payload.getFileType(), "FileType set from size");
    }
}
