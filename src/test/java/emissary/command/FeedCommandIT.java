package emissary.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
import org.hamcrest.core.StringEndsWith;
import org.hamcrest.junit.ExpectedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
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
        assertThat(command.getPriorityDirectories().size(), equalTo(1));
        assertThat(command.getPriorityDirectories().get(0).getDirectoryName(), equalTo(inputDir.toString() + "/"));
        assertThat(command.getProjectBase(), equalTo(baseDir));

        // verify defaults
        assertThat(command.getBundleSize(), equalTo(1));
        assertThat(command.getCaseClass(), equalTo(""));
        assertThat(command.getCaseId(), equalTo("auto"));
        assertThat(command.getClientPattern(), equalTo("INITIAL.FILE_PICK_UP_CLIENT.INPUT.*"));
        assertThat(command.getEatPrefix(), equalTo(""));
        assertThat(command.getHost(), equalTo("localhost"));
        assertThat(command.getLogbackConfig(), equalTo(baseDir + "/config/logback.xml"));
        assertThat(command.getOutputRoot(), StringEndsWith.endsWith("/DoneParsedData"));
        assertThat(command.getPort(), equalTo(7001));
        assertThat(command.getSort(), equalTo(null));
        assertThat(command.getWorkspaceClass(), equalTo("emissary.pickup.WorkSpace"));
        assertThat(command.getWorkspaceName(), equalTo("WorkSpace"));
        assertThat(command.isFileTimestamp(), equalTo(false));
        assertThat(command.isSimple(), equalTo(false));
        assertThat(command.isIncludeDirs(), equalTo(false));
        assertThat(command.isLoop(), equalTo(true));
        assertThat(command.isRetry(), equalTo(true));
        assertThat(command.isSkipDotFile(), equalTo(true));
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
        assertThat(System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY), equalTo("CLUSTER"));
    }

    @Test
    public void testFeedCommandGetsFlavors() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString(), "--flavor",
                "JUNK,trunk"); // trunk will be upcased
        assertThat(System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY), equalTo("CLUSTER,JUNK,TRUNK"));
    }

    @Test
    public void testFeedDedupesFlavors() throws Exception {
        FeedCommand.parse(FeedCommand.class, "-b ", baseDir.toAbsolutePath().toString(), "-i", inputDir.toAbsolutePath().toString(), "--flavor",
                "CLUSTER,FUDGE,JUNK,FUDGE");
        assertThat(System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY), equalTo("CLUSTER,FUDGE,JUNK"));

    }


}
