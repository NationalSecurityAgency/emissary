package emissary.id;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SizeIdPlaceTest extends UnitTest {

    SizeIdPlace place;

    @Override
    @Before
    public void setUp() throws IOException {

        place = new SizeIdPlace();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
    }

    @Test
    public void testSizes() {
        assertEquals("Size label chosen from size", "SIZE_SMALL", place.fileTypeBySize(2500));
    }

    @Test
    public void testZero() {
        assertEquals("Size zero chosen", "SIZE_ZERO", place.fileTypeBySize(0));
    }

    @Test
    public void testUpperBound() {
        assertEquals("Size on upper bound", "SIZE_ASTRONOMICAL", place.fileTypeBySize(Integer.MAX_VALUE));
    }

    @Test
    public void testPayload() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        place.process(payload);
        assertEquals("Current form set from size", "SIZE_TINY", payload.currentForm());
        assertEquals("FileType set from size", "SIZE_TINY", payload.getFileType());
    }
}
