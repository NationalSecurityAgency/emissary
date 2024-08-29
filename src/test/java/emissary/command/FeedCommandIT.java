package emissary.command;

import emissary.config.ConfigUtil;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.UnitTestFileUtils;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import picocli.CommandLine.ParameterException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedCommandIT extends UnitTest {

    public static final List<String> PROJECT_BASE_ARGS = List.of("-b", "--projectBase");
    public static final List<String> INPUT_ARGS = List.of("-i", "--inputRoot");
    public static final List<String> STRING_ARGS =
            List.of("-h", "--host", "-w", "--workspace", "-ci", "--caseId", "-cc", "--caseClass", "-ep", "--eatPrefix",
                    "-cs", "--case", "-o", "--outputRoot", "-ns", "--namespaceName", "--logbackConfig");
    public static final List<String> BOOLEAN_ARGS = List.of("-sd", "--skipDot", "-l", "--loop");
    public static final List<String> INT_ARGS = List.of("-p", "--port", "--bundleSize");

    @Nullable
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
        arguments.addAll(Arrays.asList(PROJECT_BASE_ARGS.get(0), baseDir.toString(), INPUT_ARGS.get(0), inputDir.toString()));

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
                PROJECT_BASE_ARGS,
                INPUT_ARGS,
                STRING_ARGS,
                BOOLEAN_ARGS,
                INT_ARGS);

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
