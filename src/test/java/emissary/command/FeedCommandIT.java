package emissary.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.beust.jcommander.ParameterException;
import emissary.config.ConfigUtil;
import emissary.test.core.UnitTest;
import emissary.util.io.UnitTestFileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class FeedCommandIT extends UnitTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();
    @DataPoints("ProjectBase Options")
    public static final String[] PROJECT_BASE_ARGS = {"-b", "--projectBase"};
    @DataPoints("Input Options")
    public static final String[] INPUT_ARGS = {"-i", "--inputRoot"};
    @DataPoints("String Options")
    public static final String[] STRING_ARGS = {"-h", "--host", "-w", "--workspace", "-ci", "--caseId", "-cc", "--caseClass", "-ep", "--eatPrefix",
            "-cs", "--case", "-o", "--outputRoot", "-ns", "--namespaceName", "--logbackConfig"};
    @DataPoints("Boolean Options")
    public static final String[] BOOLEAN_ARGS = {"-sd", "--skipDot", "-l", "--loop"};
    @DataPoints("Int Options")
    public static final String[] INT_ARGS = {"-p", "--port", "--bundleSize"};
    private FeedCommand command;
    private Path baseDir;
    private Path inputDir;
    private final List<String> arguments = new ArrayList<>();

    @Before
    public void setup() throws Exception {
        command = null;
        baseDir = Paths.get(System.getenv(ConfigUtil.PROJECT_BASE_ENV));
        inputDir = Files.createTempDirectory("input");
        arguments.clear();
    }

    @Override
    @After
    public void tearDown() throws IOException {
        UnitTestFileUtils.cleanupDirectoryRecursively(inputDir);
    }

    @Test(expected = ParameterException.class)
    public void noArguments() throws Exception {
        // test
        command = FeedCommand.parse(FeedCommand.class, Collections.emptyList());
        command.startService();
    }

    @Test
    public void requiredArgumentsDefaultValues() throws Exception {
        // setup
        // Add the required parameters
        arguments.addAll(Arrays.asList(PROJECT_BASE_ARGS[0], baseDir.toString(), INPUT_ARGS[0], inputDir.toString()));

        // test
        command = FeedCommand.parse(FeedCommand.class, arguments);

        // verify required
        assertEquals(1, command.getPriorityDirectories().size());
        assertEquals(inputDir.toString() + "/", command.getPriorityDirectories().get(0).getDirectoryName());
        assertEquals(baseDir, command.getProjectBase());

        // verify defaults
        assertEquals(1, command.getBundleSize());
        assertEquals("", command.getCaseClass());
        assertEquals("auto", command.getCaseId());
        assertEquals("INITIAL.FILE_PICK_UP_CLIENT.INPUT.*", command.getClientPattern());
        assertEquals("", command.getEatPrefix());
        assertEquals("localhost", command.getHost());
        assertEquals(baseDir + "/config/logback.xml", command.getLogbackConfig());
        assertTrue(command.getOutputRoot().endsWith("/DoneParsedData"));
        assertEquals(7001, command.getPort());
        assertNull(command.getSort());
        assertEquals("emissary.pickup.WorkSpace", command.getWorkspaceClass());
        assertEquals("WorkSpace", command.getWorkspaceName());
        assertFalse(command.isFileTimestamp());
        assertFalse(command.isSimple());
        assertFalse(command.isIncludeDirs());
        assertTrue(command.isLoop());
        assertTrue(command.isRetry());
        assertTrue(command.isSkipDotFile());
    }

    @Theory
    public void verifyExpectedOptions(@FromDataPoints("ProjectBase Options") String baseDirArg, @FromDataPoints("Input Options") String inputDirArg,
            @FromDataPoints("String Options") String stringArg, @FromDataPoints("Boolean Options") String booleanArg,
            @FromDataPoints("Int Options") String intArg) throws Exception {
        // setup
        arguments.add(baseDirArg);
        arguments.add(baseDir.toString());
        arguments.add(inputDirArg);
        arguments.add(inputDir.toString());
        arguments.add(stringArg);
        arguments.add("validateStringArg");
        arguments.add(booleanArg);
        arguments.add(intArg);
        arguments.add("4");

        // verify (no exceptions thrown)
        FeedCommand.parse(FeedCommand.class, arguments);
    }

    @Test
    public void testFeedCommandGetClusterFlavor() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString());
        assertEquals("CLUSTER", System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY));
    }

    @Test
    public void testFeedCommandGetsFlavors() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString(), "--flavor",
                "JUNK,trunk"); // trunk will be upcased
        assertEquals("CLUSTER,JUNK,TRUNK", System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY));
    }

    @Test
    public void testFeedDedupesFlavors() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString(), "--flavor",
                "CLUSTER,FUDGE,JUNK,FUDGE");
        assertEquals("CLUSTER,FUDGE,JUNK", System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY));

    }


}
