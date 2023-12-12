package emissary.output.io;

import emissary.directory.EmissaryNode;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.FileNameGenerator;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateFilterFilenameGeneratorTest extends UnitTest {

    private static final String FAKE_FILTER = "fakeFilter";
    private static final String FAKE_FILTER_DASH = "fake-filter";
    private static final String FAKE_FILTER_UNDERSCORE = "fake_filter";

    @Test
    void testDateFilterFilenameGenerator() {
        FileNameGenerator fileNameGenerator = new DateFilterFilenameGenerator(FAKE_FILTER);

        String filename1 = fileNameGenerator.nextFileName();
        String filename2 = fileNameGenerator.nextFileName();
        String[] filename1Parts = filename1.split(String.valueOf(DateFilterFilenameGenerator.DELIMITER));

        // Test Uniqueness
        assertNotEquals(filename1, filename2);

        // Test that filename starts with date (11 digits)
        assertTrue(NumberUtils.isDigits(filename1Parts[0]));
        assertEquals(11, filename1Parts[0].length());

        // Test that uuid is valid
        assertEquals(UUID.fromString(filename1Parts[1]).toString(), filename1Parts[1]);

        // Test that filename has 4 parts and filter name is present at the end
        assertEquals(4, filename1Parts.length);
        assertEquals(FAKE_FILTER, filename1Parts[filename1Parts.length - 1]);
    }

    @Test
    void testFilterDelimiterReplacement() {
        FileNameGenerator fileNameGenerator = new DateFilterFilenameGenerator(FAKE_FILTER_UNDERSCORE);
        String filename = fileNameGenerator.nextFileName();
        String[] filenameParts = filename.split(String.valueOf(DateFilterFilenameGenerator.DELIMITER));

        assertNotEquals(FAKE_FILTER_UNDERSCORE, filenameParts[3]);
        assertEquals(FAKE_FILTER_DASH, filenameParts[3]);
    }

    @Test
    void testEmptyFilter() {
        FileNameGenerator fileNameGenerator = new DateFilterFilenameGenerator(StringUtils.EMPTY);
        String filename = fileNameGenerator.nextFileName();
        String[] filenameParts = filename.split(String.valueOf(DateFilterFilenameGenerator.DELIMITER));

        // Only expect 3 parts now and that node name is the last element
        assertEquals(3, filenameParts.length);
        assertEquals(System.getProperty(EmissaryNode.NODE_NAME_PROPERTY), filenameParts[filenameParts.length - 1]);
    }


}
