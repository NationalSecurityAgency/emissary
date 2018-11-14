package emissary.util.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.Test;

/**
 * This is a little complicated to test. If these tests fail it might be because your build system doesn't copy *.dat or
 * *.xml to the build/classes area for insertion onto the classpath, or doesn't put them in the jar file on the
 * classpath. This might be, in the case of the jar file, just because this is test stuff and doesn't belong in a
 * production jar file.
 */
public class ResourceReaderTest extends UnitTest {

    @Test
    public void testResourceLocationAsTest() {
        // This tests ResourceReader via extension of UnitTest
        List<String> resources = getMyTestResources();
        assertNotNull("Resources must not be null", resources);
        assertEquals("All test resources not found", 4, resources.size());
    }


    @Test
    public void testResourceLocation() {
        ResourceReader rr = new ResourceReader();
        List<String> resources = rr.findDataResourcesFor(this.getClass());
        assertNotNull("Resources must not be null", resources);
        assertEquals("All data resources not found", 4, resources.size());

        // Make sure we built the resource names correctly
        // by opening each one as a stream
        for (String rez : resources) {
            InputStream is = null;
            is = rr.getResourceAsStream(rez);
            assertNotNull("Failed to open " + rez, is);
            try {
                is.close();
            } catch (IOException ignore) {
            }
        }


        resources = rr.findConfigResourcesFor(this.getClass());
        assertNotNull("Resources must not be null", resources);
        assertEquals("All config resources not found", 0, resources.size());

        resources = rr.findXmlResourcesFor(this.getClass());
        assertNotNull("Resources must not be null", resources);
        assertEquals("All config resources not found", 0, resources.size());

        resources = rr.findPropertyResourcesFor(this.getClass());
        assertNotNull("Resources must not be null", resources);
        assertEquals("All config resources not found", 0, resources.size());

        resources = rr.findConfigResourcesFor(emissary.util.Version.class);
        assertNotNull("Resources must not be null", resources);
        assertEquals("All config resources not found", 1, resources.size());
    }

    @Test
    public void testNaming() {
        ResourceReader rr = new ResourceReader();
        assertEquals("Resource naming", "emissary/util/Version", rr.getResourceName(emissary.util.Version.class));
        assertEquals("Resource xml naming", "emissary/util/Version.xml", rr.getXmlName(emissary.util.Version.class));
        assertEquals("Resource config naming", "emissary/util/Version.cfg", rr.getConfigDataName(emissary.util.Version.class));
        assertEquals("Resource package naming", "emissary/util/io/foo", rr.getResourceName(this.getClass().getPackage(), "foo"));
        assertEquals("Resource package naming", "emissary/util/io/foo.xml", rr.getXmlName(this.getClass().getPackage(), "foo"));
    }

}
