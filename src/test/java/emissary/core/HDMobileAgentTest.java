package emissary.core;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import emissary.place.ServiceProviderPlace;
import emissary.test.core.UnitTest;
import org.junit.Test;

/**
 *
 */
public class HDMobileAgentTest extends UnitTest {

    public HDMobileAgentTest() {}

    @Test
    public void testAtPlaceHDNull() throws Exception {
        final SimplePlace place = new SimplePlace("emissary.core.FakePlace.cfg");
        ArrayList<IBaseDataObject> children = new ArrayList<>();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(new byte[] {}, "testFile", "someFormFileType");
        children.add(ibdo);
        children.add(null);
        place.setReturnCollection(children);
        HDMobileAgent ma = new HDMobileAgent();
        List<IBaseDataObject> ret = ma.atPlaceHD(place, Collections.emptyList());
        assertTrue(ret.size() == 1);

        children.clear();
        children.add(ibdo);
        children.add(ibdo);
        ret = ma.atPlaceHD(place, Collections.emptyList());
        assertTrue(ret.size() == 2);
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
        public List<IBaseDataObject> agentProcessHeavyDuty(List<IBaseDataObject> payloadListArg) throws ResourceException {
            return children;
        }


    }
}
