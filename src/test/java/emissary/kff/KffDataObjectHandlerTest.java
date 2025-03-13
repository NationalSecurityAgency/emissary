package emissary.kff;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.channels.AbstractSeekableByteChannel;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.channels.SeekableByteChannelHelper;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import jakarta.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KffDataObjectHandlerTest extends UnitTest {
    static final byte[] DATA = "This is a test".getBytes();
    static final SeekableByteChannelFactory SBC_DATA = SeekableByteChannelHelper.memory("This is a test".getBytes());

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

    @Nullable
    protected KffDataObjectHandler kff;
    @Nullable
    protected IBaseDataObject payload;
    private static final String resource = "emissary/kff/test.dat";

    @Override
    @BeforeEach
    public void setUp() {
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

        m.clear();

        m = kff.hashData(SBC_DATA, "junk");
        assertNotNull(m.get(KffDataObjectHandler.KFF_PARAM_MD5), "Empty prefix returns normal values");
    }

    @Test
    void testMapWithNullPrefix() {
        Map<String, String> m = kff.hashData(DATA, "junk", null);
        assertNotNull(m.get(KffDataObjectHandler.KFF_PARAM_MD5), "Null prefix returns normal values");

        m.clear();

        m = kff.hashData(SBC_DATA, "junk", null);
        assertNotNull(m.get(KffDataObjectHandler.KFF_PARAM_MD5), "Null prefix returns normal values");
    }

    @Test
    void testMapWithPrefix() {
        Map<String, String> m = kff.hashData(DATA, "name", "foo");
        assertNotNull(m.get("foo" + KffDataObjectHandler.KFF_PARAM_MD5), "Prefix prepends on normal key names but we got " + m.keySet());

        m.clear();

        m = kff.hashData(SBC_DATA, "name", "foo");
        assertNotNull(m.get("foo" + KffDataObjectHandler.KFF_PARAM_MD5), "Prefix prepends on normal key names but we got " + m.keySet());
    }

    @Test
    void testHashMethod() {
        kff = new KffDataObjectHandler(true, true, true);
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_CRC32, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_CRC32, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());
        assertArrayEquals(new byte[0], payload.data());
    }

    @Test
    void testHashMethodCalledTwice() {
        // don't truncate known data or the second call will be made with an empty payload
        kff = new KffDataObjectHandler(KffDataObjectHandler.KEEP_KNOWN_DATA, true, true);
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        kff.hash(payload);

        assertNull(payload.getStringParameter(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should only be populated if hashing more than once");
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should only be populated if hashing more than once");
        assertNull(payload.getParameterAsString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should only be populated if hashing more than once");

        // hash again, to see the effect on the hash-related params.
        // none of the parameters should have a duplicated value

        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_CRC32, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_CRC32, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());
        assertNull(payload.getStringParameter(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should not be populated if hash called more than once but data hasn't changed");
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should not be populated if hash called more than once but data hasn't changed");
        assertNull(payload.getParameterAsString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should not be populated if hash called more than once but data hasn't changed");
    }

    @Test
    void testHashMethodAgainAfterModifyingData() {
        // don't truncate known data or the second call will be made with an empty payload
        kff = new KffDataObjectHandler(KffDataObjectHandler.KEEP_KNOWN_DATA, true, true);
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        kff.hash(payload);

        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_CRC32, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_CRC32, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNull(payload.getStringParameter(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should only be populated if hashing more than once and data has changed");
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should only be populated if hashing more than once and data has changed");
        assertNull(payload.getParameterAsString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should only be populated if hashing more than once and data has changed");

        payload.setData("This is a changed data".getBytes());
        // hash again, to see the effect on the hash-related params.
        // none of the parameters should have a duplicated value

        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertEquals("test.filter", payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertNotNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNotNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNotNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNotNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertNotNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertNotNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertNotNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNotNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNotNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNotNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNotNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNotNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNotNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNotNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNotNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNotEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNotEquals(DATA_MD5, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNotEquals(DATA_MD5, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNotEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertNotEquals(DATA_CRC32, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertNotEquals(DATA_CRC32, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_CRC32));
        assertNotEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNotEquals(DATA_SSDEEP, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNotEquals(DATA_SSDEEP, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNotEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNotEquals(DATA_SHA1, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNotEquals(DATA_SHA1, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNotEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNotEquals(DATA_SHA256, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNotEquals(DATA_SHA256, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());

        // make sure we've correctly populated MD5_ORIGINAL
        assertNotNull(payload.getStringParameter(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should be populated if hash called more than once");
        assertNotNull(payload.getParameterAsConcatString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should be populated if hash called more than once");
        assertNotNull(payload.getParameterAsString(KffDataObjectHandler.MD5_ORIGINAL),
                "MD5_ORIGINAL should be populated if hash called more than once");
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.MD5_ORIGINAL));
        assertEquals(DATA_MD5, payload.getParameterAsConcatString(KffDataObjectHandler.MD5_ORIGINAL));
        assertEquals(DATA_MD5, payload.getParameterAsString(KffDataObjectHandler.MD5_ORIGINAL));
    }

    @Test
    void testParentToChildMethod() {
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_DUPE_HIT, payload);
        KffDataObjectHandler.parentToChild(payload);
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_DUPE_HIT));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_DUPE_HIT));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_DUPE_HIT));
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

    @Test
    void testWithChannelFactory() {
        kff = new KffDataObjectHandler(true, true, true);
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        payload.setChannelFactory(SBC_DATA);
        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertEquals("test.filter", payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertEquals("test.filter", payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertEquals(DATA_CRC32, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertEquals(DATA_CRC32, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());
        assertArrayEquals(new byte[0], payload.data());
    }

    @Test
    void testWithEmptyChannelFactory() {
        kff = new KffDataObjectHandler(true, true, true);
        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        payload.setChannelFactory(SeekableByteChannelHelper.EMPTY_CHANNEL_FACTORY);
        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertEquals("test.filter", payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertEquals("test.filter", payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertFalse(KffDataObjectHandler.hashPresent(payload));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals("test", payload.getFileType());
        assertArrayEquals(new byte[0], payload.data());
        assertEquals(SeekableByteChannelHelper.EMPTY_CHANNEL_FACTORY, payload.getChannelFactory());
    }

    @Test
    void testNullPayload() {
        assertDoesNotThrow(() -> kff.hash(null));
    }

    @Test
    void testRemovingHash() {
        final SeekableByteChannelFactory exceptionSbcf = () -> new AbstractSeekableByteChannel() {
            @Override
            protected void closeImpl() {
                // Do nothing
            }

            @Override
            protected int readImpl(ByteBuffer byteBuffer) throws IOException {
                throw new IOException("Test exception");
            }

            @Override
            protected long sizeImpl() throws IOException {
                throw new IOException("Test exception");
            }
        };

        payload.setChannelFactory(exceptionSbcf);
        kff.hash(payload);
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertFalse(KffDataObjectHandler.hashPresent(payload));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertNull(payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNull(payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNull(payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertNotEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());

        payload.setParameter(KffDataObjectHandler.KFF_PARAM_KNOWN_FILTER_NAME, "test.filter");
        payload.setChannelFactory(SBC_DATA);
        kff.hash(payload);
        assertEquals("test.filter", payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertEquals("test.filter", payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertEquals("test.filter", payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "FILTERED_BY"));
        assertTrue(KffDataObjectHandler.hashPresent(payload));
        assertEquals(DATA_MD5, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_MD5, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_MD5));
        assertEquals(DATA_CRC32, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertEquals(DATA_CRC32, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertEquals(DATA_CRC32, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_BASE + "CRC32"));
        assertEquals(DATA_SSDEEP, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SSDEEP, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SSDEEP));
        assertEquals(DATA_SHA1, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA1, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA1));
        assertEquals(DATA_SHA256, payload.getStringParameter(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsConcatString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(DATA_SHA256, payload.getParameterAsString(KffDataObjectHandler.KFF_PARAM_SHA256));
        assertEquals(KffDataObjectHandler.KFF_DUPE_CURRENT_FORM, payload.getFileType());

    }

    @Test
    void testNullHashData() {
        assertEquals(new HashMap<>(), kff.hashData((SeekableByteChannelFactory) null, null));
    }

    @Test
    void testEmptySbcf() {
        assertEquals(new HashMap<>(), kff.hashData(SeekableByteChannelHelper.EMPTY_CHANNEL_FACTORY, null));
    }

}
