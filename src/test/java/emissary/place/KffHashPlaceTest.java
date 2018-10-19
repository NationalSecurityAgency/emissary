package emissary.place;

import static org.junit.Assert.assertTrue;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.kff.KffDataObjectHandler;
import emissary.test.core.UnitTest;
import org.junit.Test;

/**
 * Tests the KffHashPlace
 */
public class KffHashPlaceTest extends UnitTest {

    final String phonyData = "Some phony data to hash";

    public KffHashPlaceTest() {}

    @Test
    public void testProcess() throws Exception {
        final KffHashPlace place = new KffHashPlace("KffHashPlace");

        final IBaseDataObject toHash = new BaseDataObject(this.phonyData.getBytes(), "phony_data");
        place.process(toHash);
        assertTrue(KffDataObjectHandler.hashPresent(toHash));

        final IBaseDataObject orNotToHash = new BaseDataObject(this.phonyData.getBytes(), "phony_data");
        orNotToHash.setParameter(KffHashPlace.SKIP_KFF_HASH, "TRUE");
        assertTrue(!KffDataObjectHandler.hashPresent(orNotToHash));
    }
}
