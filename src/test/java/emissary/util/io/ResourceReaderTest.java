package emissary.util.io;

import emissary.test.core.junit5.UnitTest;
import emissary.util.Version;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This is a little complicated to test. If these tests fail it might be because your build system doesn't copy *.dat or
 * *.xml to the build/classes area for insertion onto the classpath, or doesn't put them in the jar file on the
 * classpath. This might be, in the case of the jar file, just because this is test stuff and doesn't belong in a
 * production jar file.
 */
class ResourceReaderTest extends UnitTest {

    @Test
    void testResourceLocationAsTest() {
        // This tests ResourceReader via extension of UnitTest
        List<String> resources = getMyTestResources();
        assertNotNull(resources, "Resources must not be null");
        assertEquals(4, resources.size(), "All test resources not found");
    }

    @Test
    void testResourceLocation() {
        ResourceReader rr = new ResourceReader();
        List<String> resources = rr.findDataResourcesFor(this.getClass());
        assertNotNull(resources, "Resources must not be null");
        assertEquals(4, resources.size(), "All data resources not found");

        // Make sure we built the resource names correctly
        // by opening each one as a stream
        for (String rez : resources) {
            InputStream is;
            is = rr.getResourceAsStream(rez);
            assertNotNull(is, "Failed to open " + rez);
            try {
                is.close();
            } catch (IOException ignore) {
            }
        }


        resources = rr.findConfigResourcesFor(this.getClass());
        assertNotNull(resources, "Resources must not be null");
        assertEquals(0, resources.size(), "All config resources not found");

        resources = rr.findXmlResourcesFor(this.getClass());
        assertNotNull(resources, "Resources must not be null");
        assertEquals(0, resources.size(), "All config resources not found");

        resources = rr.findPropertyResourcesFor(this.getClass());
        assertNotNull(resources, "Resources must not be null");
        assertEquals(0, resources.size(), "All config resources not found");

        resources = rr.findConfigResourcesFor(Version.class);
        assertNotNull(resources, "Resources must not be null");
        assertEquals(1, resources.size(), "All config resources not found");
    }

    @Test
    void testNaming() {
        ResourceReader rr = new ResourceReader();
        assertEquals("emissary/util/Version", rr.getResourceName(Version.class), "Resource naming");
        assertEquals("emissary/util/Version.xml", rr.getXmlName(Version.class), "Resource xml naming");
        assertEquals("emissary/util/Version.cfg", rr.getConfigDataName(Version.class), "Resource config naming");
        assertEquals("emissary/util/io/foo", rr.getResourceName(this.getClass().getPackage(), "foo"), "Resource package naming");
        assertEquals("emissary/util/io/foo.xml", rr.getXmlName(this.getClass().getPackage(), "foo"), "Resource package naming");
        assertEquals("emissary/util/io/sample.dat", rr.getResourceName(this.thisPackage, "sample.dat"), "Sample file with extension naming");
    }

}
