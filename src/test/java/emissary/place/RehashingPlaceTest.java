package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.SessionParser;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class RehashingPlaceTest extends UnitTest {

    private static final byte[] configKeyData = ("TGT_HOST = \"myhost.example.com\"\n" + "TGT_PORT = \"9999\"\n"
            + "SERVICE_KEY = \"TPROXY.TRANSFORM.TNAME.http://@{TGT_HOST}:@{TGT_PORT}/TPlaceName$5050\"\n" + "SERVICE_DESCRIPTION = \"test place\"\n")
                    .getBytes();

    @Test
    public void testRehash() throws Exception {
        byte[] DATA = "abcdefghijklmnopqrstuvwxyz".getBytes();
        byte[] DOUBLE_DATA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes();


        InputStream config = new ByteArrayInputStream(configKeyData);
        RehashPlaceTest tp = new RehashPlaceTest(config);
        IBaseDataObject payload = new BaseDataObject(DATA, "file1", "FORM1");
        KffDataObjectHandler kff = tp.getKffHandler();
        assertNotNull("Kff handler must be iinitialized in a place " + "that implements the RehashingPlace interface", kff);

        // Simulate previous activity that would set the length and
        // hash values to the current data
        kff.hash(payload);
        payload.setParameter(SessionParser.ORIG_DOC_SIZE_KEY, Integer.valueOf(payload.dataLength()));

        // Save off the previous hash values
        String oldhash = KffDataObjectHandler.getHashValue(payload);

        // Tell the place how to change the data
        tp.setUseData(DOUBLE_DATA);

        // Process the payload
        tp.agentProcessCall(payload);

        // Check the new ORIG SIZE parameter
        assertEquals("Original Size must be reset on rehash", "" + payload.dataLength(), payload.getStringParameter(SessionParser.ORIG_DOC_SIZE_KEY));

        // Check the new hash values
        String newhash = KffDataObjectHandler.getHashValue(payload);

        assertTrue("Hash value must change in a rehashing place when the data changes", !oldhash.equals(newhash));

    }

    private static final class RehashPlaceTest extends ServiceProviderPlace implements RehashingPlace {

        private byte[] USE_DATA = null;

        public RehashPlaceTest(InputStream config) throws IOException {
            super(config);
        }

        @Override
        public void process(IBaseDataObject d) {
            assertNotNull(d);
            d.setData(USE_DATA);
        }

        public KffDataObjectHandler getKffHandler() {
            return kff;
        }

        public void setUseData(byte[] data) {
            USE_DATA = data;
        }

    }

}
