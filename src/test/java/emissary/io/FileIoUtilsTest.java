package emissary.io;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static emissary.util.io.FileIoUtils.cleanSpecPath;
import static emissary.util.io.FileIoUtils.filePathIsWithinBaseDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileIoUtilsTest extends UnitTest {

    @TempDir
    public Path testOutputFolder;

    @Test
    void testFilePathIsWithinBaseDirectory() {

        String basePath = testOutputFolder.resolve("foo").toString();
        assertEquals(basePath + "/somefile", filePathIsWithinBaseDirectory(basePath, basePath + "/somefile"));
        assertEquals(basePath + "/otherfile", filePathIsWithinBaseDirectory(basePath, basePath + "//otherfile"));
        assertEquals(basePath + "/foo/otherfile", filePathIsWithinBaseDirectory(basePath, basePath + "/./foo/otherfile"));
        assertEquals(basePath + "/sub/otherfile", filePathIsWithinBaseDirectory(basePath, basePath + "/sub/././otherfile"));

        // Each of these should have thrown an Exception
        assertThrows(IllegalArgumentException.class, () -> filePathIsWithinBaseDirectory(basePath, "/var/log/somelog"));

        assertThrows(IllegalArgumentException.class,
                () -> filePathIsWithinBaseDirectory(basePath, basePath + "/../foo2/otherfile"),
                "Expected an IllegalArgumentException from input " + basePath + "/../foo2/otherfile");

        assertThrows(IllegalArgumentException.class,
                () -> filePathIsWithinBaseDirectory(basePath, basePath + "/../../somefile"),
                "Expected an IllegalArgumentException from input " + basePath + "/../../somefile");

        assertThrows(IllegalArgumentException.class,
                () -> filePathIsWithinBaseDirectory(basePath, basePath + "/path/../../../otherpath"),
                "Expected an IllegalArgumentException from input " + basePath + "/path/../../../otherpath");
    }

    @Test
    void testCleanSpecPath() {
        assertEquals("/this/is/fine", cleanSpecPath("/this/is/fine"));
        assertEquals("/this/./is/fine", cleanSpecPath("/this/../is/fine"));
        assertEquals("/this/./is/./fine", cleanSpecPath("/this/../is/../fine"));
        assertEquals("/this/./././/./is/fine", cleanSpecPath("/this/....../../..//./is/fine"));
    }
}
