package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataDenyListTest extends UnitTest {

    MetadataDenyList getDenyList(final Configurator config) {
        MetadataDenyList list = new MetadataDenyList();
        list.configure(config);
        return list;
    }

    @Test
    void testParameterDenyOnly() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("DENYLIST_FIELD", "DROP");
        config.addEntry("DENYLIST_PREFIX", "DROP_");

        MetadataDenyList list = getDenyList(config);

        assertFalse(list.isAllowed("DROP"), "denylisted field should be denied");
        assertFalse(list.isAllowed("DROP_ME"), "denylisted prefix should be denied");
        assertTrue(list.isAllowed("KEEP"), "unlisted field should be allowed");
        assertTrue(list.isAllowed("KEEP_ME"), "unlisted prefix field should be allowed");
    }

    @Test
    void testParameterDenyAllowsUnlisted() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("DENYLIST_FIELD", "DROP");

        MetadataDenyList list = getDenyList(config);

        assertFalse(list.isAllowed("DROP"), "denylisted field should be denied");
        assertTrue(list.isAllowed("FOO"), "unlisted field should be allowed when only a deny list is configured");
    }

    @Test
    void testParameterValueDeny() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("DENYLIST_VALUE_KEY", "SECRET");

        MetadataDenyList list = getDenyList(config);

        assertTrue(list.isAllowed("KEY", "public"), "non-denied value should be allowed");
        assertFalse(list.isAllowed("KEY", "SECRET"), "denied value should be rejected");
    }

    @Test
    void testStripPrefix() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("STRIP_PARAM_PREFIX", "APP_");

        MetadataDenyList list = getDenyList(config);

        assertTrue(list.stripPrefix("APP_FOO").equals("FOO"), "configured prefix should be stripped");
        assertTrue(list.stripPrefix("OTHER").equals("OTHER"), "non-matching name should be unchanged");
    }
}
