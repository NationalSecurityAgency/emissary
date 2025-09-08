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
    static Path rhel95Path;
    @SuppressWarnings("NonFinalStaticField")
    static Path ubuntu20Path;
    @SuppressWarnings("NonFinalStaticField")
    static Path pop22Path;

    @BeforeAll
    static void getResources() throws Exception {
        ResourceReader rr = new ResourceReader();

        centos7Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "centos7")).toURI());
        rhel8Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "rhel8")).toURI());
        rhel95Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "rhel95")).toURI());
        ubuntu20Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "ubuntu20")).toURI());
        pop22Path = Path.of(rr.getResource(rr.getResourceName(OSReleaseUtil.class.getPackage(), "pop22")).toURI());
    }

    @Test
    void testGetVersionId() {
        assertEquals("7", OSReleaseUtil.getVersionId(centos7Path));
        assertEquals("8.10", OSReleaseUtil.getVersionId(rhel8Path));
        assertEquals("9.5", OSReleaseUtil.getVersionId(rhel95Path));
        assertEquals("20.04", OSReleaseUtil.getVersionId(ubuntu20Path));
        assertEquals("22.04", OSReleaseUtil.getVersionId(pop22Path));
    }

    @Test
    void testGetMajorVersion() {
        assertEquals("7", OSReleaseUtil.getMajorReleaseVersion(centos7Path));
        assertEquals("8", OSReleaseUtil.getMajorReleaseVersion(rhel8Path));
        assertEquals("9", OSReleaseUtil.getMajorReleaseVersion(rhel95Path));
        assertEquals("20", OSReleaseUtil.getMajorReleaseVersion(ubuntu20Path));
        assertEquals("22", OSReleaseUtil.getMajorReleaseVersion(pop22Path));
    }

    @Test
    void testIsUbuntu() {
        assertFalse(OSReleaseUtil.isUbuntu(centos7Path));
        assertFalse(OSReleaseUtil.isUbuntu(rhel8Path));
        assertFalse(OSReleaseUtil.isUbuntu(rhel95Path));
        assertTrue(OSReleaseUtil.isUbuntu(ubuntu20Path));
        assertTrue(OSReleaseUtil.isUbuntu(pop22Path));
    }

    @Test
    void testIsCentOS() {
        assertTrue(OSReleaseUtil.isCentOs(centos7Path));
        assertFalse(OSReleaseUtil.isCentOs(rhel8Path));
        assertFalse(OSReleaseUtil.isCentOs(rhel95Path));
        assertFalse(OSReleaseUtil.isCentOs(ubuntu20Path));
        assertFalse(OSReleaseUtil.isCentOs(pop22Path));
    }

    @Test
    void testIsRhel() {
        assertFalse(OSReleaseUtil.isRhel(centos7Path));
        assertTrue(OSReleaseUtil.isRhel(rhel8Path));
        assertTrue(OSReleaseUtil.isRhel(rhel95Path));
        assertFalse(OSReleaseUtil.isRhel(ubuntu20Path));
        assertFalse(OSReleaseUtil.isRhel(pop22Path));
    }
}
