package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import com.google.common.collect.TreeMultimap;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.MetadataDictionary;
import emissary.core.MetadataDictionaryTest;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class MetadataDictionaryUtilTest extends UnitTest {

    MetadataDictionary dict;
    private String lines;
    private MetadataDictionaryUtil md;

    @Before
    public void setup() throws IOException {
        final Configurator conf = ConfigUtil.getConfigInfo(MetadataDictionaryTest.class);
        this.dict = new MetadataDictionary("test", conf);
        this.lines = "key val1\nxyzzy val2\nanother val3\n";
        this.md = new MetadataDictionaryUtil(this.dict, "");
    }

    @Test
    public void keysCanMapToMultipleVals() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final Map<String, Collection<String>> map = this.md.convertLinesToMap(this.lines.getBytes(), output);

            assertEquals(2, map.size());
            assertEquals(2, map.get("NothingHappens").size());
            assertTrue(map.get("NothingHappens").contains("val2"));
            assertTrue(map.get("NothingHappens").contains("val3"));

            assertEquals(1, map.get("key").size());
            assertTrue(map.get("key").contains("val1"));
        } catch (IOException e) {
            fail("Exception occurred while converting lines to map");
        }
    }

    @Test
    public void mapConvertsToByteArray() throws IOException {
        final TreeMultimap<String, String> kv = TreeMultimap.create();
        kv.put("abc", "val1");
        kv.put("zzz", "val2");
        kv.put("xyzzy", "val3");
        kv.put("another", "val4");

        final byte[] mapped = MetadataDictionaryUtil.convertMapToByteArray(kv.asMap());
        assertEquals("abc val1\nanother val4\nxyzzy val3\nzzz val2\n", new String(mapped));

        final byte[] mappedAgain = this.md.map(mapped);
        assertEquals("NothingHappens val3\nNothingHappens val4\nabc val1\nzzz val2\n", new String(mappedAgain));
    }
}
