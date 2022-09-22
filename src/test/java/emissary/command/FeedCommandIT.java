package emissary.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.Lists;
import emissary.config.ConfigUtil;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.UnitTestFileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class FeedCommandIT extends UnitTest {

    public static final String[] PROJECT_BASE_ARGS = {"-b", "--projectBase"};
    public static final String[] INPUT_ARGS = {"-i", "--inputRoot"};
    public static final String[] STRING_ARGS = {"-h", "--host", "-w", "--workspace", "-ci", "--caseId", "-cc", "--caseClass", "-ep", "--eatPrefix",
            "-cs", "--case", "-o", "--outputRoot", "-ns", "--namespaceName", "--logbackConfig"};
    public static final String[] BOOLEAN_ARGS = {"-sd", "--skipDot", "-l", "--loop"};
    public static final String[] INT_ARGS = {"-p", "--port", "--bundleSize"};

    private FeedCommand command;
    private Path baseDir;
    private Path inputDir;
    private final List<String> arguments = new ArrayList<>();

    @TempDir
    public Path tmpDir;

    @BeforeEach
    public void setup() throws Exception {
        command = null;
        baseDir = Paths.get(System.getenv(ConfigUtil.PROJECT_BASE_ENV));
        inputDir = Files.createTempDirectory(tmpDir, "input");
        arguments.clear();
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        UnitTestFileUtils.cleanupDirectoryRecursively(inputDir);
    }

    @Test
    void noArguments() throws Exception {
        // test
        command = FeedCommand.parse(FeedCommand.class, Collections.emptyList());
        assertThrows(ParameterException.class, () -> command.startService());
    }

    @Test
    void requiredArgumentsDefaultValues() throws Exception {
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

    static class CartesianArgumentsProvider implements ArgumentsProvider {
        List<List<String>> cartesian = Lists.cartesianProduct(
                Arrays.asList(PROJECT_BASE_ARGS),
                Arrays.asList(INPUT_ARGS),
                Arrays.asList(STRING_ARGS),
                Arrays.asList(BOOLEAN_ARGS),
                Arrays.asList(INT_ARGS));

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return cartesian.stream().map(list -> Arguments.of((Object[]) list.toArray(new String[0])));
        }
    }

    @ParameterizedTest
    @ArgumentsSource(CartesianArgumentsProvider.class)
    void verifyExpectedOptions(String baseDirArg,
            String inputDirArg,
            String stringArg,
            String booleanArg,
            String intArg) {
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
        assertDoesNotThrow(() -> FeedCommand.parse(FeedCommand.class, arguments));
    }

    @Test
    void testFeedCommandGetClusterFlavor() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString());
        assertEquals("CLUSTER", System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY));
    }

    @Test
    void testFeedCommandGetsFlavors() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString(), "--flavor",
                "JUNK,trunk"); // trunk will be upcased
        assertEquals("CLUSTER,JUNK,TRUNK", System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY));
    }

    @Test
    void testFeedDedupesFlavors() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString(), "--flavor",
                "CLUSTER,FUDGE,JUNK,FUDGE");
        assertEquals("CLUSTER,FUDGE,JUNK", System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY));
    }

}
