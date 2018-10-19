package emissary.kff;

import static org.junit.Assert.assertTrue;

import java.util.Map;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KffDataObjectHandlerTest extends UnitTest {
    static final byte[] DATA = "This is a test".getBytes();

    // echo -n "This is a test" | openssl sha1
    static final String DATA_SHA1 = "a54d88e06612d820bc3be72877c74f257b561b19";

    // echo -n "This is a test" | openssl md5
    static final String DATA_MD5 = "ce114e4501d2f4e2dcea3e17b546f339";

    protected KffDataObjectHandler kff;

    @Override
    @Before
    public void setUp() throws Exception {
        kff = new KffDataObjectHandler();

    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        kff = null;
    }

    @Test
    public void testMapWithEmptyPrefix() {
        Map<String, String> m = kff.hashData(DATA, "junk");
        assertTrue("Empty prefix returns normal values", m.get(KffDataObjectHandler.KFF_PARAM_MD5) != null);
    }

    @Test
    public void testMapWithNullPrefix() {
        Map<String, String> m = kff.hashData(DATA, "junk", null);
        assertTrue("Null prefix returns normal values", m.get(KffDataObjectHandler.KFF_PARAM_MD5) != null);
    }

    @Test
    public void testMapWithPrefix() {
        Map<String, String> m = kff.hashData(DATA, "name", "foo");
        assertTrue("Prefix prepends on normal key names but we got " + m.keySet(), m.get("foo" + KffDataObjectHandler.KFF_PARAM_MD5) != null);
    }
}
