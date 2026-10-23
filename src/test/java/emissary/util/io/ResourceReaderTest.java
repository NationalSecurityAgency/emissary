package emissary.util.io;

import emissary.test.core.junit5.UnitTest;
import emissary.util.Version;
import emissary.util.io.fixtures.TomlOnlyFixture;
import emissary.util.io.fixtures.YamlOnlyFixture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
            InputStream is = rr.getResourceAsStream(rez);
            assertNotNull(is, "Failed to open " + rez);
            try {
                is.close();
            } catch (IOException ignored) {
                // ignored.
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

    @Test
    void testFindConfigDataName() {
        ResourceReader rr = new ResourceReader();
        assertEquals("emissary/util/Version.cfg", rr.findConfigDataName(Version.class), "Existing .cfg wins");
        assertEquals("emissary/util/io/fixtures/YamlOnlyFixture.yaml", rr.findConfigDataName(YamlOnlyFixture.class),
                "Falls back to the YAML resource");
        assertEquals("emissary/util/io/fixtures/YamlOnlyFixture.yaml", rr.findConfigDataName(new YamlOnlyFixture()),
                "Object overload falls back too");
        assertEquals("emissary/util/io/fixtures/TomlOnlyFixture.toml", rr.findConfigDataName(TomlOnlyFixture.class),
                "Falls back to the TOML resource");
        assertNull(rr.findConfigDataName(String.class), "No config resource means null");
    }

    @Test
    void testGetConfigDataAsStreamStaysLegacy() throws Exception {
        // Backward compatible: the original method only ever sees .cfg, exactly as before.
        final ResourceReader rr = new ResourceReader();
        assertNull(rr.getConfigDataAsStream(YamlOnlyFixture.class), "YAML-only class means no legacy stream");
        assertNull(rr.getConfigDataAsStream(TomlOnlyFixture.class), "TOML-only class means no legacy stream");
        try (InputStream is = rr.getConfigDataAsStream(Version.class)) {
            assertNotNull(is, "Existing .cfg still resolves");
        }
    }


    @Test
    void testFindConfigResourcesStructured() {
        // The reviewer's repro: single-format resources must all be discoverable from one suffix list.
        ResourceReader rr = new ResourceReader();
        assertEquals(List.of("emissary/util/io/fixtures/YamlOnlyFixture.yaml"),
                rr.findConfigResourcesFor(YamlOnlyFixture.class));
        assertEquals(List.of("emissary/util/io/fixtures/TomlOnlyFixture.toml"),
                rr.findConfigResourcesFor(TomlOnlyFixture.class));
    }

}
