package emissary.util.io;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class GreedyResourceReaderTest extends UnitTest {


    @Test
    void testPayloadFileLocation() {

        // files in the "payloads" subdirectory should be found as resources
        List<String> testFileNames = Arrays.asList("payloads/File1.txt", "payloads/subdir/sample.md");
        // Non-payload.txt is in the test class directory, but not beneath its "payloads" subdirectory
        final String NON_PAYLOAD = "Non-payload.txt";

        GreedyResourceReader grr = new GreedyResourceReader();
        String testClassDir = grr.getResourceName(this.getClass());

        List<String> resources = grr.findAllPayloadFilesFor(this.getClass());
        assertNotNull(resources, "Resources must not be null");
        assertEquals(testFileNames.size(), resources.size(), "All data resources not found");

        testFileNames.stream()
                .map(t -> Path.of(testClassDir, t))
                .forEach(p -> assertTrue(resources.contains(p.toString())));

        assertFalse(resources.contains(Path.of(testClassDir, NON_PAYLOAD).toString()));

        for (String rez : resources) {
            try (InputStream is = grr.getResourceAsStream(rez)) {
                assertNotNull(is, "Failed to open " + rez);
            } catch (IOException e) {
                fail("Failed to open " + rez, e);
            }
        }
    }


    @Test
    void testAnswerFileLocation() {

        // files in the "payloads" subdirectory should be found as resources
        List<String> testAnswerFileNames = Arrays.asList("answers/File1.txt.xml", "answers/subdir/sample.md.xml");

        // files that should NOT be detected as "answer" files based on their locations
        List<String> misplacedAnswerFileNames = Arrays.asList("Non-answer.xml", "answers/README");

        GreedyResourceReader grr = new GreedyResourceReader();
        String testClassDir = grr.getResourceName(this.getClass());

        List<String> answerFiles = grr.findAllAnswerFilesFor(this.getClass());
        assertNotNull(answerFiles, "Resources must not be null");
        assertEquals(testAnswerFileNames.size(), answerFiles.size(), "Not all answer files not found");

        testAnswerFileNames.stream()
                .map(t -> Path.of(testClassDir, t))
                .forEach(p -> assertTrue(answerFiles.contains(p.toString())));

        misplacedAnswerFileNames.stream()
                .map(t -> Path.of(testClassDir, t))
                .forEach(p -> assertFalse(answerFiles.contains(p.toString())));


        for (String file : answerFiles) {
            try (InputStream is = grr.getResourceAsStream(file)) {
                assertNotNull(is, "Failed to open " + file);
            } catch (IOException e) {
                fail("Failed to open " + file, e);
            }
        }
    }
}
