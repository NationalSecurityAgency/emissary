package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

public class MetadataDictionaryTest extends UnitTest {
    private static final String TEST_NAMESPACE = "test_namespace";

    private MetadataDictionary getDict(boolean bind) {
        MetadataDictionary m = null;
        try {
            Configurator conf = ConfigUtil.getConfigInfo(this.getClass());
            assertNotNull(conf, "Could not find config for MetadataDictionaryTest");
            if (bind) {
                m = MetadataDictionary.initialize(TEST_NAMESPACE, conf);
            } else {
                m = new MetadataDictionary(TEST_NAMESPACE, conf);
            }
        } catch (Exception ex) {
            fail("Exception configuring dictionary: " + ex.getMessage());
        }
        assertNotNull(m, "Metadata Dictionary must be created");
        return m;
    }

    private MetadataDictionary getDict() {
        return getDict(false);
    }

    private void clearNamespace() {
        emissary.core.Namespace.unbind(TEST_NAMESPACE);
    }

    @Test
    void testRename() {
        MetadataDictionary d = getDict();
        assertEquals("bar", d.rename("foo"), "Renaming for found value");
    }

    @Test
    void testNonRename() {
        MetadataDictionary d = getDict();
        assertEquals("baz", d.rename("baz"), "Original name for not-found value");
    }

    @Test
    void testRegexRename() {
        MetadataDictionary d = getDict();
        assertEquals("^bar^", d.regex("abc_bar_def"), "Rename by regex match");
    }

    @Test
    void testRegexNonRename() {
        MetadataDictionary d = getDict();
        assertEquals("shazam", d.regex("shazam"), "Original name when no regex match");
    }

    @Test
    void testFullMapRename() {
        MetadataDictionary d = getDict();
        assertEquals("NothingHappens", d.map("xyzzy"), "Rename on exact match via map");
    }

    @Test
    void testFullMapRegex() {
        MetadataDictionary d = getDict();
        assertEquals("NothingHappensCaseInsensitively", d.map("xyZZy"), "Rename on regex match via map");
    }

    @Test
    void testFullMapNoMatch() {
        MetadataDictionary d = getDict();
        assertEquals("Hello", d.map("Hello"), "NO match via map gives original name");
    }


    @Test
    void testNamespace() {
        try {
            MetadataDictionary d1 = getDict(true);
            MetadataDictionary d2 = MetadataDictionary.lookup(TEST_NAMESPACE);
            assertEquals(d1, d2, "INitialize must bind by default lookup name");
        } catch (Exception ex) {
            fail("Exception doing namespace lookup: " + ex.getMessage());
        } finally {
            clearNamespace();
        }
    }

}
