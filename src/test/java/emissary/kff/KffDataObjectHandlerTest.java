package emissary.kff;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KffDataObjectHandlerTest extends UnitTest {
    static final byte[] DATA = "This is a test".getBytes();

    // echo -n "This is a test" | openssl sha1
    static final String DATA_SHA1 = "a54d88e06612d820bc3be72877c74f257b561b19";

    // echo -n "This is a test" | openssl md5
    static final String DATA_MD5 = "ce114e4501d2f4e2dcea3e17b546f339";

    // echo -n "This is a test" | openssl sha256
    static final String DATA_SHA256 = "c7be1ed902fb8dd4d48997c6452f5d7e509fbcdbe2808b16bcf4edce4c07d14e";

    // echo -n "This is a test" | openssl sha384
    static final String DATA_SHA384 =
            "a27c7667e58200d4c0688ea136968404a0da366b1a9fc19bb38a0c7a609a1eef2bcc82837f4f4d92031a66051494b38c";

    // echo -n "This is a test" | openssl sha512
    static final String DATA_SHA512 =
            "a028d4f74b602ba45eb0a93c9a4677240dcf281a1a9322f183bd32f0bed82ec72de9c3957b2f4c9a1ccf7ed14f85d73498df38017e703d47ebb9f0b3bf116f69";

    static final String DATA_SSDEEP = "3:hMCEpn:hup";

    static final String DATA_CRC32 = "33323239323631363138";


    protected KffDataObjectHandler kff;
    protected IBaseDataObject payload;
    private static final String resource = "emissary/kff/test.dat";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        kff = new KffDataObjectHandler();
        try (InputStream doc = new ResourceReader().getResourceAsStream(resource)) {
            byte[] data = IOUtils.toByteArray(doc);
            String defaultCurrentForm = "test";
            payload = DataObjectFactory.getInstance(data, resource, defaultCurrentForm);
        } catch (IOException e) {
            fail("Error getting resource file");
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        kff = null;
        payload = null;
    }

    @Test
    void testMapWithEmptyPrefix() {
        Map<String, String> m = kff.hashData(DATA, "junk");
        assertNotNull(m.get(KffDataObjectHandler.KFF_PARAM_MD5), "Empty prefix returns normal values");
    }

    @Test
    void testMapWithNullPrefix() {
        Map<String, String> m = kff.hashData(DATA, "junk", null);
        assertNotNull(m.get(KffDataObjectHandler.KFF_PARAM_MD5), "Null prefix returns normal values");
    }

    @Test
    void testMapWithPrefix() {
        Map<String, String> m = kff.hashData(DATA, "name", "foo");
        assertNotNull(m.get("foo" + KffDataObjectHandler.KFF_PARAM_MD5), "Prefix prepends on normal key names but we got " + m.keySet());
    }

    @Test
    void testHashMethod() {
        kff = new KffDataObjectHandler(true, true, true);
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());
        assertArrayEquals(new byte[0], payload.data());
    }

    @Test
    void testHashMethodCalledTwice() {
        // don't truncate known data or the second call will be made with an empty payload
        kff = new KffDataObjectHandler(KffDataObjectHandler.KEEP_KNOWN_DATA, true, true);
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        kff.hash(payload);

        // hash again, to see the effect on the hash-related params.
        // none of the parameters should have a duplicated value

        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());
    }

    @Test
    void testParentToChildMethod() {
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_DUPE_HIT, payload);
        KffDataObjectHandler.parentToChild(payload);
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_DUPE_HIT));
    }

    @Test
    void testSetAndGetHash() {
        KffDataObjectHandler.setHashValue(payload, DATA_MD5);
        assertEquals(DATA_MD5, KffDataObjectHandler.getMd5Value(payload));
        KffDataObjectHandler.setHashValue(payload, DATA_SHA1);
        assertEquals(DATA_SHA1, KffDataObjectHandler.getHashValue(payload));
        KffDataObjectHandler.setHashValue(payload, DATA_SSDEEP);
        assertEquals(DATA_SSDEEP, KffDataObjectHandler.getSsdeepValue(payload));
        KffDataObjectHandler.setHashValue(payload, DATA_SHA256);
        assertEquals(DATA_SHA256, KffDataObjectHandler.getSha256Value(payload));
        KffDataObjectHandler.setHashValue(payload, DATA_SHA384);
        assertEquals(DATA_SHA384, KffDataObjectHandler.getSha384Value(payload));
        KffDataObjectHandler.setHashValue(payload, DATA_SHA512);
        assertEquals(DATA_SHA512, KffDataObjectHandler.getSha512Value(payload));

        assertEquals(DATA_SHA512, KffDataObjectHandler.getBestAvailableHash(payload));
        payload.deleteParameter(KffDataObjectHandler.KFF_PARAM_SHA512);
        assertEquals(DATA_SHA384, KffDataObjectHandler.getBestAvailableHash(payload));
    }
}
