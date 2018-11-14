package emissary.core;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DataObjectFactoryTest extends UnitTest {
    private String defaultPayloadClass;

    @Override
    @Before
    public void setUp() throws Exception {
        this.defaultPayloadClass = DataObjectFactory.getImplementingClass();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        DataObjectFactory.setImplementingClass(this.defaultPayloadClass);
    }

    /**
     * Test configuration
     */
    @Test
    public void testFactory() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        assertNotNull("DataObject created", d);
        assertTrue("Proper class hierarchy", d instanceof IBaseDataObject);
    }

    @Test
    public void testSetImpl() {
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        assertEquals("Impl class set", MyDataObject.class.getName(), DataObjectFactory.getImplementingClass());
        final IBaseDataObject d = DataObjectFactory.getInstance();
        assertNotNull("DataObject created", d);
        assertTrue("Proper class hierarchy", d instanceof IBaseDataObject);
        assertTrue("Proper class hierarchy", d instanceof BaseDataObject);
        assertTrue("Proper class hierarchy", d instanceof MyDataObject);
    }

    @Test
    public void testWithArgs() {
        final Object[] args = new Object[] {"This is a test".getBytes(), "TestItem"};
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        final IBaseDataObject d = DataObjectFactory.getInstance(args);
        assertNotNull("DataObject created", d);
    }

    @Test
    public void testWtihFullSet() {
        byte[] testPayload = "This is a test".getBytes();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(testPayload, "filename", "form", "type");
        assertThat(ibdo.getFilename(), equalTo("filename"));
        assertThat(ibdo.currentForm(), equalTo("form"));
        assertThat(ibdo.getFileType(), equalTo("type"));
        assertThat(ibdo.data(), equalTo(testPayload));
    }

    @Test
    public void testFormAndFileType() {
        byte[] testPayload = "This is a test".getBytes();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(testPayload, "filename", "formAndFileType");
        assertThat(ibdo.getFilename(), equalTo("filename"));
        assertThat(ibdo.currentForm(), equalTo("formAndFileType"));
        assertThat(ibdo.getFileType(), equalTo("formAndFileType"));
        assertThat(ibdo.data(), equalTo(testPayload));
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
