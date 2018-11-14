package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MultiFileServerPlaceTest extends UnitTest {
    MFSPlace mfsp = null;
    IBaseDataObject parent = null;

    @Override
    @Before
    public void setUp() throws Exception {


        ResourceReader rr = new ResourceReader();
        InputStream is = null;
        try {
            is = rr.getConfigDataAsStream(this.getClass());
            mfsp = new MFSPlace(is, "THIS.THAT.OTHER.http://localhost:8888/MFSPlace");
        } catch (Exception ex) {
            logger.error("Cannot create MFSPlace", ex);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ignore) {
                // empty catch block
            }
        }
        parent = DataObjectFactory.getInstance("This is the parent data".getBytes(), "/name/of/the/parent", "PARENT_FORM");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mfsp.shutDown();
        mfsp = null;
        parent = null;
    }

    @Test
    public void testClassificationCopy() {
        parent.setClassification("FOO//BAR//BAZ");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals("Classification must be copied", parent.getClassification(), children.get(0).getClassification());
    }


    @Test
    public void testMetadataCopy() {
        parent.setParameter("FOO", "BAR");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertNull("Metadata must not be copied unless configured", children.get(0).getStringParameter("FOO"));
    }

    @Test
    public void testCurrentForms() {
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals("Parent form should not change", "PARENT_FORM", parent.currentForm());
        assertEquals("Child form is wrong", "CHILD_FORM", children.get(0).currentForm());
    }

    @Test
    public void testCopyOfMultiValuedParam() {
        parent.appendParameter("COPY_ME", "BAR1");
        parent.appendParameter("COPY_ME", "BAR2");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals("Child parameter copy must maintain separate multi-valued values", 2, children.get(0).getParameter("COPY_ME").size());
    }

    @Test
    public void testFileTypeHandling() {
        mfsp.cft = "CHILD_WOW";
        parent.setFileType("PARENT_WOW");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals("Child filetype must be preserved for default call", "CHILD_WOW", children.get(0).getFileType());
    }

    @Test
    public void testTransformHistoryCopy() {
        parent.appendTransformHistory("a.b.c.http://localhost:8888/defg");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertTrue("Child must have parent transform history", children.get(0).hasVisited("a.b.c.*"));
    }

    @Test
    public void testFileTypeRemoval() {
        parent.setFileType("PARENT_FT");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertTrue("Child must not have parent file type", children.get(0).getFileType() == null);
    }

    private static final class MFSPlace extends MultiFileServerPlace {

        public String cft = null;

        public MFSPlace(InputStream config, String loc) throws IOException {
            super(config, loc);
        }

        @Override
        public void process(IBaseDataObject payload) {
            fail("Not expected to call this method");
        }

        @Override
        protected void configurePlace() {}

        @Override
        public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payload) {
            // Does nothing but creates a sprout to use.
            List<IBaseDataObject> sprouts = new ArrayList<IBaseDataObject>();

            IBaseDataObject s = DataObjectFactory.getInstance();
            s.setFilename(payload.getFilename() + Family.initial());
            s.setCurrentForm("CHILD_FORM");
            s.setData("This is the child data".getBytes());
            if (cft != null) {
                s.setFileType(cft);
            }
            sprouts.add(s);

            // Perform the normal processing
            addParentInformation(payload, sprouts);
            return sprouts;
        }
    }

    @Test
    public void testValidateDataHook() {
        IBaseDataObject d = DataObjectFactory.getInstance();
        d.setData(new byte[] {'1'});
        IBaseDataObject emptyd = DataObjectFactory.getInstance();
        assertTrue("failed to validate " + d, mfsp.shouldProcess(d));
        assertFalse("failed to reject " + emptyd, mfsp.shouldProcess(emptyd));
        assertFalse("failed to reject null", mfsp.shouldProcess(null));
    }
}
