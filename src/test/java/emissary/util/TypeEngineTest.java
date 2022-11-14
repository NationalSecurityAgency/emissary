package emissary.util;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryException;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TypeEngineTest extends UnitTest {
    @BeforeEach
    public void configureTest() throws EmissaryException {
        setConfig(null, true);

    }

    @Test
    void testEngineNonConfiguration() {
        final TypeEngine engine = new TypeEngine();
        assertNull(engine.getForm("foo", "whatever"), "Unconfigured engine should return null");
    }

    @Test
    void testEngineBadConfiguration() {
        final Configurator conf = new ServiceConfigGuide();
        conf.addEntry("TYPE_ENGINE_FILE", "foo-types.cfg");
        final TypeEngine engine = new TypeEngine(conf);
        assertNull(engine.getForm("foo", "whatever"), "Mis-configured engine should return null");
    }

    @Test
    void testEngineConfiguration() {
        final Configurator conf = new ServiceConfigGuide();
        conf.addEntry("TYPE_ENGINE_FILE", "test-types.cfg");
        final TypeEngine engine = new TypeEngine(conf);
        assertEquals("XYZZY", engine.getForm("test", "WHATEVER"), "Configured engine should load types");
        assertNull(engine.getForm("foo", "WHATEVER"), "Configured engine should handle unknown engine type");
        assertNull(engine.getForm("foo", null), "Configured engine should handle null label");
        assertNull(engine.getForm(null, "WHATEVER"), "Configured engine should handle null engine");

        // Now add an "extra" type
        engine.addType("test", "WHATEVER", "newstuff");
        assertEquals("newstuff", engine.getForm("test", "WHATEVER"), "Extra types must override configured types");

        // Try it by file extension
        assertEquals("newstuff", engine.getFormByExtension("test", "blah.WHATEVER"), "File extension chopping must still match");
        assertEquals("newstuff", engine.getFormByExtension("test", "WHATEVER"), "File extension chopping must still match");
    }
}
