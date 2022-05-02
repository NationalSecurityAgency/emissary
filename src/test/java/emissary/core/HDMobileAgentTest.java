package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import emissary.place.ServiceProviderPlace;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

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
