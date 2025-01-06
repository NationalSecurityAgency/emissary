package emissary.util.os;

import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OSReleaseUtilTest {

    @Test
    void testGetVersionId() throws Exception {
        ResourceReader rr = new ResourceReader();

        assertEquals("7", OSReleaseUtil.getVersionId(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "centos7")).toURI())));
        assertEquals("8.10", OSReleaseUtil.getVersionId(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "rhel8")).toURI())));
        assertEquals("20.04",
                OSReleaseUtil.getVersionId(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "ubuntu")).toURI())));
    }

    @Test
    void testGetMajorVersion() throws Exception {
        ResourceReader rr = new ResourceReader();

        assertEquals("7",
                OSReleaseUtil.getMajorVersion(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "centos7")).toURI())));
        assertEquals("8", OSReleaseUtil.getMajorVersion(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "rhel8")).toURI())));
        assertEquals("20",
                OSReleaseUtil.getMajorVersion(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "ubuntu")).toURI())));
    }

    @Test
    void testIsUbuntu() throws Exception {
        ResourceReader rr = new ResourceReader();

        assertFalse(OSReleaseUtil.isUbuntu(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "centos7")).toURI())));
        assertFalse(OSReleaseUtil.isUbuntu(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "rhel8")).toURI())));
        assertTrue(OSReleaseUtil.isUbuntu(Path.of(rr.getResource(rr.getResourceName(this.getClass().getPackage(), "ubuntu")).toURI())));
    }
}
