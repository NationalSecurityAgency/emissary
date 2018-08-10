package emissary.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.beust.jcommander.JCommander;
import emissary.config.ConfigUtil;
import emissary.test.core.UnitTest;
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
public class WhatCommandIT extends UnitTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();
    @DataPoints("ProjectBase Options")
    public static final String[] PROJECT_BASE_ARGS = {"-b", "--projectBase"};
    @DataPoints("Input Options")
    public static final String[] INPUT_ARGS = {"-i", "--input"};
    @DataPoints("Boolean Options Without Value")
    public static final String[] BOOLEAN_ARGS_WITHOUT_VALUE = {"-r", "--recursive"};
    @DataPoints("Boolean Options With Value")
    public static final String[] BOOLEAN_ARGS_WITH_VALUE = {"-h", "--header"};
    @DataPoints("String Options")
    public static final String[] STRING_ARGS = {"--logbackConfig"};
    private WhatCommand command;
    private Path baseDir;
    private Path inputDir;
    private final List<String> arguments = new ArrayList<>();

    @Before
    @Override
    public void setUp() throws Exception {
        command = null;
        baseDir = Paths.get(System.getenv(ConfigUtil.PROJECT_BASE_ENV));
        inputDir = Files.createTempDirectory("input");
        arguments.clear();
    }

    @Theory
    public void verifyExpectedOptions(@FromDataPoints("ProjectBase Options") String baseDirArg, @FromDataPoints("Input Options") String inputDirArg,
            @FromDataPoints("String Options") String stringArg, @FromDataPoints("Boolean Options Without Value") String booleanArgWithoutValue,
            @FromDataPoints("Boolean Options With Value") String booleanArgWithValue) throws Exception {
        // setup
        arguments.add(baseDirArg);
        arguments.add(baseDir.toString());
        arguments.add(inputDirArg);
        arguments.add(inputDir.toString());
        arguments.add(stringArg);
        arguments.add("validateStringArg");
        arguments.add(booleanArgWithoutValue);
        arguments.add(booleanArgWithValue);
        arguments.add(Boolean.FALSE.toString());

        // test (no exceptions thrown)
        WhatCommand.parse(WhatCommand.class, arguments);
    }

    @Test(expected = RuntimeException.class)
    public void missingInputDirectory() throws Exception {
        // setup
        arguments.add(PROJECT_BASE_ARGS[0]);
        arguments.add(baseDir.toString());
        arguments.add(INPUT_ARGS[0]);
        arguments.add("missingInputDirectory");

        // test
        WhatCommand.parse(WhatCommand.class, arguments);

        // verify
        exception.expectMessage("The option '-i' was configured with path 'missingInputDirectory' which does not exist");
    }

    @Test(expected = RuntimeException.class)
    public void missingConfigDirectory() throws Exception {
        // setup
        arguments.add(PROJECT_BASE_ARGS[0]);
        arguments.add("missingConfigDirectory");
        arguments.add(INPUT_ARGS[0]);
        arguments.add(inputDir.toAbsolutePath().toString());

        // test
        WhatCommand.parse(WhatCommand.class, arguments);

        // verify
        exception.expectMessage("The option '-b' was configured with path 'missingInputDirectory' which does not exist");
    }

    @Test(expected = RuntimeException.class)
    public void unreadableInput() throws Exception {
        // setup
        arguments.add(PROJECT_BASE_ARGS[0]);
        arguments.add(baseDir.toString());
        arguments.add(INPUT_ARGS[0]);
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(inputDir, perms);
        arguments.add(inputDir.toAbsolutePath().toString());
        command = WhatCommand.parse(WhatCommand.class, arguments);

        // test
        command.run(new JCommander());

        // verify
        exception.expectMessage("The option '-i' was configured with path '" + inputDir + "' which is not readable");
    }

}
