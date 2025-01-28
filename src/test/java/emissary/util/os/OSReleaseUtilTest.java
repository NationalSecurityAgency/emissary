package emissary.util.os;

import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OSReleaseUtilTest {

    @SuppressWarnings("NonFinalStaticField")
    static Path centos7Path;
    @SuppressWarnings("NonFinalStaticField")
    static Path rhel8Path;
    @SuppressWarnings("NonFinalStaticField")
    static Path ubuntu20Path;

    @BeforeAll
    static void getResources() throws Exception {
        ResourceReader rr = new ResourceReader();

        centos7Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "centos7")).toURI());
        rhel8Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "rhel8")).toURI());
        ubuntu20Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "ubuntu20")).toURI());
    }

    @Test
    void testGetVersionId() {
        assertEquals("7", OSReleaseUtil.getVersionId(centos7Path));
        assertEquals("8.10", OSReleaseUtil.getVersionId(rhel8Path));
        assertEquals("20.04", OSReleaseUtil.getVersionId(ubuntu20Path));
    }

    @Test
    void testGetMajorVersion() {
        assertEquals("7", OSReleaseUtil.getMajorReleaseVersion(centos7Path));
        assertEquals("8", OSReleaseUtil.getMajorReleaseVersion(rhel8Path));
        assertEquals("20", OSReleaseUtil.getMajorReleaseVersion(ubuntu20Path));
    }

    @Test
    void testIsUbuntu() {
        assertFalse(OSReleaseUtil.isUbuntu(centos7Path));
        assertFalse(OSReleaseUtil.isUbuntu(rhel8Path));
        assertTrue(OSReleaseUtil.isUbuntu(ubuntu20Path));
    }

    @Test
    void testIsCentOS() {
        assertTrue(OSReleaseUtil.isCentOs(centos7Path));
        assertFalse(OSReleaseUtil.isCentOs(rhel8Path));
        assertFalse(OSReleaseUtil.isCentOs(ubuntu20Path));
    }

    @Test
    void testIsRhel() {
        assertFalse(OSReleaseUtil.isRhel(centos7Path));
        assertTrue(OSReleaseUtil.isRhel(rhel8Path));
        assertFalse(OSReleaseUtil.isRhel(ubuntu20Path));
    }
}
