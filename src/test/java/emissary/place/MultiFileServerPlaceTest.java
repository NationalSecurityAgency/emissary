package emissary.place;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiFileServerPlaceTest extends UnitTest {
    MFSPlace mfsp = null;
    IBaseDataObject parent = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {

        ResourceReader rr = new ResourceReader();
        try (InputStream is = rr.getConfigDataAsStream(this.getClass())) {
            mfsp = new MFSPlace(is, "THIS.THAT.OTHER.http://localhost:8888/MFSPlace");
        } catch (Exception ex) {
            logger.error("Cannot create MFSPlace", ex);
        }
        parent = DataObjectFactory.getInstance("This is the parent data".getBytes(UTF_8), "/name/of/the/parent", "PARENT_FORM");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        mfsp.shutDown();
        mfsp = null;
        parent = null;
    }

    @Test
    void testClassificationCopy() {
        parent.setClassification("FOO//BAR//BAZ");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals(parent.getClassification(), children.get(0).getClassification(), "Classification must be copied");
    }


    @Test
    void testMetadataCopy() {
        parent.setParameter("FOO", "BAR");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertNull(children.get(0).getStringParameter("FOO"), "Metadata must not be copied unless configured");
    }

    @Test
    void testCurrentForms() {
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals("PARENT_FORM", parent.currentForm(), "Parent form should not change");
        assertEquals("CHILD_FORM", children.get(0).currentForm(), "Child form is wrong");
    }

    @Test
    void testCopyOfMultiValuedParam() {
        parent.appendParameter("COPY_ME", "BAR1");
        parent.appendParameter("COPY_ME", "BAR2");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals(2, children.get(0).getParameter("COPY_ME").size(), "Child parameter copy must maintain separate multi-valued values");
    }

    @Test
    void testFileTypeHandling() {
        mfsp.cft = "CHILD_WOW";
        parent.setFileType("PARENT_WOW");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertEquals("CHILD_WOW", children.get(0).getFileType(), "Child filetype must be preserved for default call");
    }

    @Test
    void testTransformHistoryCopy() {
        parent.appendTransformHistory("a.b.c.http://localhost:8888/defg");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertTrue(children.get(0).hasVisited("a.b.c.*"), "Child must have parent transform history");
    }

    @Test
    void testFileTypeRemoval() {
        parent.setFileType("PARENT_FT");
        List<IBaseDataObject> children = mfsp.processHeavyDuty(parent);
        assertNull(children.get(0).getFileType(), "Child must not have parent file type");
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
            List<IBaseDataObject> sprouts = new ArrayList<>();

            IBaseDataObject s = DataObjectFactory.getInstance();
            s.setFilename(payload.getFilename() + Family.initial());
            s.setCurrentForm("CHILD_FORM");
            s.setData("This is the child data".getBytes(UTF_8));
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
    void testValidateDataHook() {
        IBaseDataObject d = DataObjectFactory.getInstance();
        d.setData(new byte[] {'1'});
        IBaseDataObject emptyd = DataObjectFactory.getInstance();
        assertTrue(mfsp.shouldProcess(d), "failed to validate " + d);
        assertFalse(mfsp.shouldProcess(emptyd), "failed to reject " + emptyd);
        assertFalse(mfsp.shouldProcess(null), "failed to reject null");
    }
}
