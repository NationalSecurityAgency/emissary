package emissary.output.filter;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import emissary.util.JavaCharSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AbstractFilterTest extends UnitTest {

    private static final String LOWERCASE_STRING = "abc";
    private static final byte[] LOWERCASE_DATA = LOWERCASE_STRING.getBytes(StandardCharsets.UTF_8);
    private static final byte[] UPPERCASE_DATA = "ABC".getBytes(StandardCharsets.UTF_8);
    private static final String CONFIG_DATA = "OUTPUT_TYPE = \"ZEN.Language\"\n"
            + "OUTPUT_TYPE = \"JPEG.PrimaryView\"\n"
            + "OUTPUT_TYPE = \"BAR.Properties\"\n"
            + "OUTPUT_TYPE = \"FOOZ\"\n"
            + "OUTPUT_TYPE = \"WORD.XML\"\n";

    private final Set<String> PRIMARY_TYPES_TO_CHECK = new HashSet<>(
            Arrays.asList("FOO.PrimaryView", "FOO", "*.Language", "*.PrimaryView", "NONE.Language"));
    private final Set<String> TYPES_TO_CHECK_FOR_NAMED_VIEW = new HashSet<>(Arrays.asList("ZEN", "BAR", "ZEN.FOO.BAR", "FOO", "BAR.BAR", "ZEN.BAR",
            "FOO.BAR", "*.BAR", "ZEN.FOO", "ZEN.BAR.BAR"));
    private TestFilter tf;

    @Before
    @Override
    public void setUp() {
        tf = new TestFilter();
        try {
            Configurator myConfig = ConfigUtil.getConfigInfo(new ByteArrayInputStream(CONFIG_DATA.getBytes(StandardCharsets.UTF_8)), "testconfig");
            tf.initialize(myConfig);
        } catch (IOException e) {
            fail("Could not configure test filter: " + e.getMessage());
        }
    }

    @After
    @Override
    public void tearDown() {
        tf = null;
    }

    @Test
    public void testGetCharset() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "FOO");
        Map<String, String> charsets = new HashMap<>();
        charsets.put("ASCII", "UTF-8");
        JavaCharSet.initialize(charsets);
        d.putParameter("HTML_CHARSET", "ASCII");
        assertEquals("Charset ", "UTF-8", tf.getCharset(d, "BAR"));
    }

    @Test
    public void testNormalizeBytes() {
        assertEquals("Charset ", LOWERCASE_STRING, tf.normalizeBytes(LOWERCASE_DATA, "UTF-8"));
        assertEquals("Charset ", LOWERCASE_STRING, tf.normalizeBytes(LOWERCASE_DATA, "JUNK"));
    }

    @Test
    public void testPrimaryTypesToCheck() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "FOO");
        assertEquals("Primary Types mismatch", PRIMARY_TYPES_TO_CHECK, tf.getPrimaryTypesToCheck(d));
    }

    @Test
    public void testSimpleOutputtable() {
        assertTrue("Should output", tf.isOutputtable("FOOZ"));
        assertFalse("Should not output", tf.isOutputtable("BEAR"));
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "FOO");
        assertTrue("Should output", tf.isOutputtable(d));
        Map<String, Object> params = new HashMap<>();
        params.put("Junk", new byte[0]);
        assertTrue("Should output", tf.isOutputtable(d, params));
    }

    @Test
    public void testTypesToCheckForNamedView() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "FOO");
        d.setCurrentForm("BAR");
        d.putParameter("LANGUAGE", "ZEN");
        assertEquals("Types for named view mismatch", TYPES_TO_CHECK_FOR_NAMED_VIEW, tf.getTypesToCheckForNamedView(d, "BAR"));
    }

    @Test
    public void testNotOutputtable() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "BAR");
        d.addAlternateView("Rotten", UPPERCASE_DATA);
        assertFalse("Data is not outputtable by primary view", tf.isOutputtable(tf.getTypesToCheck(d)));
    }

    @Test
    public void testOutputtableByFiletype() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "BAR");
        d.setFileType("FOOZ");
        assertTrue("Data is outputtable by file type", tf.isOutputtable(tf.getTypesToCheck(d)));
    }

    @Test
    public void testOutputtableByLanguage() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "BAR");
        d.putParameter("LANGUAGE", "ZEN");
        assertTrue("Data is outputtable by language", tf.isOutputtable(tf.getTypesToCheck(d)));
    }

    @Test
    public void testOutputtableByPrimaryViewOnly() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "JPEG");
        d.addAlternateView("Rotten", UPPERCASE_DATA);
        assertTrue("Data is outputtable by primary view", tf.isOutputtable(tf.getTypesToCheck(d)));
    }

    @Test
    public void testOutputtableByType() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "FOOZ");
        assertTrue("Data is outputtable by current form", tf.isOutputtable(tf.getTypesToCheck(d)));
    }

    @Test
    public void testOutputtableByViewType() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "BAR");
        d.addAlternateView("Properties", UPPERCASE_DATA);
        assertTrue("Data is outputtable by view type", tf.isOutputtable(tf.getTypesToCheck(d)));
    }

    @Test
    public void testOutputtableByViewTypeWildcard() {
        IBaseDataObject d = DataObjectFactory.getInstance(LOWERCASE_DATA, "shortname", "WORD");
        d.addAlternateView("XML:test.xml", UPPERCASE_DATA);
        assertTrue("Data is outputtable by alt view type", tf.isOutputtable(tf.getTypesToCheck(d)));
    }

    static class TestFilter extends AbstractFilter {
        void initialize(emissary.config.Configurator configG) {
            super.initialize(configG, getFilterName(), configG);
        }

        @Override
        public int filter(IBaseDataObject d, Map<String, Object> params) {
            return STATUS_FAILURE;
        }
    }
}
