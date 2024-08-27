package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataObjectFactoryTest extends UnitTest {
    private String defaultPayloadClass;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.defaultPayloadClass = DataObjectFactory.getImplementingClass();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        DataObjectFactory.setImplementingClass(this.defaultPayloadClass);
    }

    /**
     * Test configuration
     */
    @Test
    void testFactory() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        assertNotNull(d, "DataObject created");
        assertInstanceOf(IBaseDataObject.class, d, "Proper class hierarchy");
    }

    @Test
    void testSetImpl() {
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        assertEquals(MyDataObject.class.getName(), DataObjectFactory.getImplementingClass(), "Impl class set");
        final IBaseDataObject d = DataObjectFactory.getInstance();
        assertNotNull(d, "DataObject created");
        assertInstanceOf(BaseDataObject.class, d, "Proper class hierarchy");
        assertInstanceOf(MyDataObject.class, d, "Proper class hierarchy");

        final IBaseDataObject e = DataObjectFactory.getInstance(true);
        assertNotNull(e, "DataObject created");
        assertInstanceOf(BaseDataObject.class, e, "Proper class hierarchy");
        assertInstanceOf(MyDataObject.class, e, "Proper class hierarchy");
        assertTrue(e.isExtracted());
    }

    @Test
    void testWithArgs() {
        Object[] args = new Object[] {"This is a test".getBytes(), "TestItem"};
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        final IBaseDataObject d = DataObjectFactory.getInstance(args);
        assertNotNull(d, "DataObject created");

        args = new Object[] {"This is a test".getBytes(), "TestItem", true};
        final IBaseDataObject e = DataObjectFactory.getInstance(args);
        assertNotNull(e, "DataObject created");
        assertTrue(e.isExtracted());
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
        assertTrue(extract.isExtracted());
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
        assertTrue(extract.isExtracted());
    }

    @SuppressWarnings("unused")
    public static class MyDataObject extends BaseDataObject {
        private static final long serialVersionUID = -2254597461746556210L;

        public MyDataObject() {
            super();
        }

        public MyDataObject(final boolean extracted) {
            super(extracted);
        }

        public MyDataObject(final byte[] data, final String name) {
            super(data, name);
        }

        public MyDataObject(final byte[] data, final String name, final boolean extracted) {
            super(data, name, extracted);
        }
    }
}
