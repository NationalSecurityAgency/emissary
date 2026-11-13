package emissary.core;

import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 *
 */
class HDMobileAgentTest extends UnitTest {

    public HDMobileAgentTest() {}

    @Test
    void testAtPlaceHDNull() throws Exception {
        final SimplePlace place = new SimplePlace("emissary.core.FakePlace.cfg");
        ArrayList<IBaseDataObject> children = new ArrayList<>();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(new byte[] {}, "testFile", "someFormFileType");
        children.add(ibdo);
        children.add(null);
        place.setReturnCollection(children);
        HDMobileAgent ma = new HDMobileAgent();
        List<IBaseDataObject> ret = ma.atPlaceHD(place, Collections.emptyList());
        assertEquals(1, ret.size());

        children.clear();
        children.add(ibdo);
        children.add(ibdo);
        ret = ma.atPlaceHD(place, Collections.emptyList());
        assertEquals(2, ret.size());
    }

    @Test
    void testAddPayloadBackfillsTld() {
        final HDMobileAgent agent = new HDMobileAgent();
        final IBaseDataObject root = DataObjectFactory.getInstance(new byte[] {}, "testFile");
        agent.addPayload(root);

        assertNull(root.getTld());

        final IBaseDataObject child = DataObjectFactory.getInstance(new byte[] {}, "testFile-att-1");
        final IBaseDataObject grandchild = DataObjectFactory.getInstance(new byte[] {}, "testFile-att-1-att-1");
        agent.addPayload(List.of(child, grandchild));

        assertSame(root, child.getTld());
        assertSame(root, grandchild.getTld());
        assertNull(root.getTld());
    }

    @Test
    void testAddPayloadKeepsExistingTld() {
        final HDMobileAgent agent = new HDMobileAgent();
        final IBaseDataObject root = DataObjectFactory.getInstance(new byte[] {}, "testFile");
        agent.addPayload(root);

        final IBaseDataObject actualTld = DataObjectFactory.getInstance(new byte[] {}, "otherFile");
        final IBaseDataObject child = DataObjectFactory.getInstance(new byte[] {}, "testFile-att-1", "", actualTld);
        agent.addPayload(child);

        assertSame(actualTld, child.getTld());
        assertNotSame(root, child.getTld());
    }

    static final class SimplePlace extends ServiceProviderPlace {

        private List<IBaseDataObject> children = Collections.emptyList();

        public SimplePlace(String configInfo) throws IOException {
            super(configInfo, "SimplePlace.www.example.com:8001");
        }

        void setReturnCollection(List<IBaseDataObject> children) {
            this.children = children;
        }

        @Override
        public List<IBaseDataObject> agentProcessHeavyDuty(List<IBaseDataObject> payloadListArg) {
            return children;
        }


    }
}
