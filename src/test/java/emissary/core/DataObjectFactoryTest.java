package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DataObjectFactoryTest extends UnitTest {
    private String defaultPayloadClass;
    private String defaultPayloadExtractClass;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.defaultPayloadClass = DataObjectFactory.getImplementingClass();
        this.defaultPayloadExtractClass = DataObjectFactory.getImplementingExtractClass();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        DataObjectFactory.setImplementingClass(this.defaultPayloadClass);
        DataObjectFactory.setImplementingExtractClass(this.defaultPayloadExtractClass);
    }

    /**
     * Test configuration
     */
    @Test
    void testFactory() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        assertNotNull(d, "DataObject created");
        assertInstanceOf(IBaseDataObject.class, d, "Proper class hierarchy");

        final IBaseDataObject e = DataObjectFactory.getInstance(true);
        assertNotNull(e, "DataObject created");
        assertInstanceOf(ExtractedRecord.class, e, "Proper class hierarchy");
    }

    @Test
    void testSetImpl() {
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        assertEquals(MyDataObject.class.getName(), DataObjectFactory.getImplementingClass(), "Impl class set");
        final IBaseDataObject d = DataObjectFactory.getInstance();
        assertNotNull(d, "DataObject created");
        assertInstanceOf(BaseDataObject.class, d, "Proper class hierarchy");
        assertInstanceOf(MyDataObject.class, d, "Proper class hierarchy");

        DataObjectFactory.setImplementingExtractClass(MyExtractObject.class.getName());
        assertEquals(MyExtractObject.class.getName(), DataObjectFactory.getImplementingExtractClass(), "Impl class set");
        final IBaseDataObject e = DataObjectFactory.getInstance(true);
        assertNotNull(e, "DataObject created");
        assertInstanceOf(BaseDataObject.class, e, "Proper class hierarchy");
        assertInstanceOf(ExtractedRecord.class, e, "Proper class hierarchy");
        assertInstanceOf(MyExtractObject.class, e, "Proper class hierarchy");
    }

    @Test
    void testWithArgs() {
        final Object[] args = new Object[] {"This is a test".getBytes(), "TestItem"};
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        final IBaseDataObject d = DataObjectFactory.getInstance(args);
        assertNotNull(d, "DataObject created");

        DataObjectFactory.setImplementingExtractClass(MyExtractObject.class.getName());
        final IBaseDataObject e = DataObjectFactory.getInstance(true, args);
        assertNotNull(e, "DataObject created");
    }

    @Test
    void testWtihFullSet() {
        byte[] testPayload = "This is a test".getBytes();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(testPayload, "filename", "form", "type");
        assertEquals("filename", ibdo.getFilename());
        assertEquals("form", ibdo.currentForm());
        assertEquals("type", ibdo.getFileType());
        assertSame(testPayload, ibdo.data());

        IBaseDataObject extract = DataObjectFactory.getInstance(testPayload, "filename", "form", "type", true);
        assertEquals("filename", extract.getFilename());
        assertEquals("form", extract.currentForm());
        assertEquals("type", extract.getFileType());
        assertSame(testPayload, extract.data());
    }

    @Test
    void testFormAndFileType() {
        byte[] testPayload = "This is a test".getBytes();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(testPayload, "filename", "formAndFileType");
        assertEquals("filename", ibdo.getFilename());
        assertEquals("formAndFileType", ibdo.currentForm());
        assertEquals("formAndFileType", ibdo.getFileType());
        assertSame(testPayload, ibdo.data());

        IBaseDataObject extract = DataObjectFactory.getInstance(testPayload, "filename", "formAndFileType", true);
        assertEquals("filename", extract.getFilename());
        assertEquals("formAndFileType", extract.currentForm());
        assertEquals("formAndFileType", extract.getFileType());
        assertSame(testPayload, extract.data());
    }

    @SuppressWarnings("unused")
    public static class MyDataObject extends BaseDataObject {
        private static final long serialVersionUID = -2254597461746556210L;

        public MyDataObject() {
            super();
        }

        public MyDataObject(final byte[] data, final String name) {
            super(data, name);
        }
    }

    @SuppressWarnings("unused")
    public static class MyExtractObject extends ExtractedRecord {
        private static final long serialVersionUID = -579253286374306668L;

        public MyExtractObject() {
            super();
        }

        public MyExtractObject(final byte[] data, final String name) {
            super(data, name);
        }
    }
}
