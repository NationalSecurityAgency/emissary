package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class MetadataDictionaryTest extends UnitTest {
    private static final String TEST_NAMESPACE = "test_namespace";

    private MetadataDictionary getDict(boolean bind) {
        MetadataDictionary m = null;
        try {
            Configurator conf = ConfigUtil.getConfigInfo(this.getClass());
            assertNotNull("Could not find config for MetadataDictionaryTest", conf);
            if (bind) {
                m = MetadataDictionary.initialize(TEST_NAMESPACE, conf);
            } else {
                m = new MetadataDictionary(TEST_NAMESPACE, conf);
            }
        } catch (Exception ex) {
            fail("Exception configuring dictionary: " + ex.getMessage());
        }
        assertNotNull("Metadata Dictionary must be created", m);
        return m;
    }

    private MetadataDictionary getDict() {
        return getDict(false);
    }

    private void clearNamespace() {
        emissary.core.Namespace.unbind(TEST_NAMESPACE);
    }

    @Test
    public void testRename() {
        MetadataDictionary d = getDict();
        assertEquals("Renaming for found value", "bar", d.rename("foo"));
    }

    @Test
    public void testNonRename() {
        MetadataDictionary d = getDict();
        assertEquals("Original name for not-found value", "baz", d.rename("baz"));
    }

    @Test
    public void testRegexRename() {
        MetadataDictionary d = getDict();
        assertEquals("Rename by regex match", "^bar^", d.regex("abc_bar_def"));
    }

    @Test
    public void testRegexNonRename() {
        MetadataDictionary d = getDict();
        assertEquals("Original name when no regex match", "shazam", d.regex("shazam"));
    }

    @Test
    public void testFullMapRename() {
        MetadataDictionary d = getDict();
        assertEquals("Rename on exact match via map", "NothingHappens", d.map("xyzzy"));
    }

    @Test
    public void testFullMapRegex() {
        MetadataDictionary d = getDict();
        assertEquals("Rename on regex match via map", "NothingHappensCaseInsensitively", d.map("xyZZy"));
    }

    @Test
    public void testFullMapNoMatch() {
        MetadataDictionary d = getDict();
        assertEquals("NO match via map gives original name", "Hello", d.map("Hello"));
    }


    @Test
    public void testNamespace() {
        try {
            MetadataDictionary d1 = getDict(true);
            MetadataDictionary d2 = MetadataDictionary.lookup(TEST_NAMESPACE);
            assertEquals("INitialize must bind by default lookup name", d1, d2);
        } catch (Exception ex) {
            fail("Exception doing namespace lookup: " + ex.getMessage());
        } finally {
            clearNamespace();
        }
    }

}
