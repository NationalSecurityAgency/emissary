package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertTrue(d instanceof IBaseDataObject, "Proper class hierarchy");
    }

    @Test
    void testSetImpl() {
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        assertEquals(MyDataObject.class.getName(), DataObjectFactory.getImplementingClass(), "Impl class set");
        final IBaseDataObject d = DataObjectFactory.getInstance();
        assertNotNull(d, "DataObject created");
        assertTrue(d instanceof IBaseDataObject, "Proper class hierarchy");
        assertTrue(d instanceof BaseDataObject, "Proper class hierarchy");
        assertTrue(d instanceof MyDataObject, "Proper class hierarchy");
    }

    @Test
    void testWithArgs() {
        final Object[] args = new Object[] {"This is a test".getBytes(), "TestItem"};
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        final IBaseDataObject d = DataObjectFactory.getInstance(args);
        assertNotNull(d, "DataObject created");
    }

    @Test
    void testWtihFullSet() {
        byte[] testPayload = "This is a test".getBytes();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(testPayload, "filename", "form", "type");
        assertEquals("filename", ibdo.getFilename());
        assertEquals("form", ibdo.currentForm());
        assertEquals("type", ibdo.getFileType());
        assertSame(testPayload, ibdo.data());
    }

    @Test
    void testFormAndFileType() {
        byte[] testPayload = "This is a test".getBytes();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(testPayload, "filename", "formAndFileType");
        assertEquals("filename", ibdo.getFilename());
        assertEquals("formAndFileType", ibdo.currentForm());
        assertEquals("formAndFileType", ibdo.getFileType());
        assertSame(testPayload, ibdo.data());
    }

    public static class MyDataObject extends BaseDataObject {
        static final long serialVersionUID = -2254597461746556210L;

        public MyDataObject() {
            super();
        }

        public MyDataObject(final byte[] data, final String name) {
            super(data, name);
        }
    }
}
