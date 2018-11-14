package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryException;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class TypeEngineTest extends UnitTest {
    @Before
    public void configureTest() throws EmissaryException {
        setConfig(null, true);

    }

    @Test
    public void testEngineNonConfiguration() {
        final TypeEngine engine = new TypeEngine();
        assertNull("Unconfigured engine should return null", engine.getForm("foo", "whatever"));
    }

    @Test
    public void testEngineBadConfiguration() {
        final Configurator conf = new ServiceConfigGuide();
        conf.addEntry("TYPE_ENGINE_FILE", "foo-types.cfg");
        final TypeEngine engine = new TypeEngine(conf);
        assertNull("Mis-configured engine should return null", engine.getForm("foo", "whatever"));
    }

    @Test
    public void testEngineConfiguration() {
        final Configurator conf = new ServiceConfigGuide();
        conf.addEntry("TYPE_ENGINE_FILE", "test-types.cfg");
        final TypeEngine engine = new TypeEngine(conf);
        assertEquals("Configured engine should load types", "XYZZY", engine.getForm("test", "WHATEVER"));
        assertNull("Configured engine should handle unknown engine type", engine.getForm("foo", "WHATEVER"));
        assertNull("Configured engine should handle null label", engine.getForm("foo", null));
        assertNull("Configured engine should handle null engine", engine.getForm(null, "WHATEVER"));

        // Now add an "extra" type
        engine.addType("test", "WHATEVER", "newstuff");
        assertEquals("Extra types must override configured types", "newstuff", engine.getForm("test", "WHATEVER"));

        // Try it by file extension
        assertEquals("File extension chopping must still match", "newstuff", engine.getFormByExtension("test", "blah.WHATEVER"));
        assertEquals("File extension chopping must still match", "newstuff", engine.getFormByExtension("test", "WHATEVER"));
    }
}
