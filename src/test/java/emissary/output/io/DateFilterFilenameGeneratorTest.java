package emissary.output.io;

import emissary.directory.EmissaryNode;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.FileNameGenerator;

import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
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
        List<String> filename1Parts = Splitter.onPattern(String.valueOf(DateFilterFilenameGenerator.DELIMITER)).splitToList(filename1);

        // Test Uniqueness
        assertNotEquals(filename1, filename2);

        // Test that filename starts with date (11 digits)
        assertTrue(NumberUtils.isDigits(filename1Parts.get(0)));
        assertEquals(11, filename1Parts.get(0).length());

        // Test that uuid is valid
        assertEquals(UUID.fromString(filename1Parts.get(1)).toString(), filename1Parts.get(1));

        // Test that filename has 4 parts and filter name is present at the end
        assertEquals(4, filename1Parts.size());
        assertEquals(FAKE_FILTER, filename1Parts.get(filename1Parts.size() - 1));
    }

    @Test
    void testFilterDelimiterReplacement() {
        FileNameGenerator fileNameGenerator = new DateFilterFilenameGenerator(FAKE_FILTER_UNDERSCORE);
        String filename = fileNameGenerator.nextFileName();
        List<String> filenameParts = Splitter.onPattern(String.valueOf(DateFilterFilenameGenerator.DELIMITER)).splitToList(filename);

        assertNotEquals(FAKE_FILTER_UNDERSCORE, filenameParts.get(3));
        assertEquals(FAKE_FILTER_DASH, filenameParts.get(3));
    }

    @Test
    void testEmptyFilter() {
        FileNameGenerator fileNameGenerator = new DateFilterFilenameGenerator(StringUtils.EMPTY);
        String filename = fileNameGenerator.nextFileName();
        List<String> filenameParts = Splitter.onPattern(String.valueOf(DateFilterFilenameGenerator.DELIMITER)).splitToList(filename);

        // Only expect 3 parts now and that node name is the last element
        assertEquals(3, filenameParts.size());
        assertEquals(System.getProperty(EmissaryNode.NODE_NAME_PROPERTY), filenameParts.get(filenameParts.size() - 1));
    }


}
