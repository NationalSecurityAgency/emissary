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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatCommandIT extends UnitTest {

    public static final String[] PROJECT_BASE_ARGS = {"-b", "--projectBase"};
    public static final String[] INPUT_ARGS = {"-i", "--input"};
    public static final String[] BOOLEAN_ARGS_WITHOUT_VALUE = {"-r", "--recursive"};
    public static final String[] BOOLEAN_ARGS_WITH_VALUE = {"-h", "--header"};
    public static final String[] STRING_ARGS = {"--logbackConfig"};

    private WhatCommand command;
    private Path baseDir;
    private Path inputDir;
    private final List<String> arguments = new ArrayList<>();

    @TempDir
    public Path tmpDir;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        command = null;
        baseDir = Paths.get(System.getenv(ConfigUtil.PROJECT_BASE_ENV));
        inputDir = Files.createTempDirectory(tmpDir, "input");
        arguments.clear();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        UnitTestFileUtils.cleanupDirectoryRecursively(inputDir);
    }

    static class CartesianArgumentsProvider implements ArgumentsProvider {
        List<List<String>> cartesian = Lists.cartesianProduct(
                Arrays.asList(PROJECT_BASE_ARGS),
                Arrays.asList(INPUT_ARGS),
                Arrays.asList(STRING_ARGS),
                Arrays.asList(BOOLEAN_ARGS_WITHOUT_VALUE),
                Arrays.asList(BOOLEAN_ARGS_WITH_VALUE));

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
            String booleanArgWithoutValue,
            String booleanArgWithValue) {
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
        assertDoesNotThrow(() -> WhatCommand.parse(WhatCommand.class, arguments));
    }

    @Test
    void missingInputDirectory() {
        // setup
        arguments.add(PROJECT_BASE_ARGS[0]);
        arguments.add(baseDir.toString());
        arguments.add(INPUT_ARGS[0]);
        arguments.add("missingInputDirectory");

        // test
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> WhatCommand.parse(WhatCommand.class, arguments));
        // verify
        assertTrue(thrown.getMessage().contains("The option '-i' was configured with path 'missingInputDirectory' which does not exist"));
    }

    @Test
    void missingConfigDirectory() {
        // setup
        arguments.add(PROJECT_BASE_ARGS[0]);
        arguments.add("missingConfigDirectory");
        arguments.add(INPUT_ARGS[0]);
        arguments.add(inputDir.toAbsolutePath().toString());

        // test
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> WhatCommand.parse(WhatCommand.class, arguments));
        // verify
        assertTrue(thrown.getMessage().contains("The option '-b' was configured with path 'missingConfigDirectory' which does not exist"));
    }

    @Test
    void unreadableInput() throws Exception {
        // setup
        arguments.add(PROJECT_BASE_ARGS[0]);
        arguments.add(baseDir.toString());
        arguments.add(INPUT_ARGS[0]);
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(inputDir, perms);
        arguments.add(inputDir.toAbsolutePath().toString());

        // test
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> WhatCommand.parse(WhatCommand.class, arguments));

        // Reset perms for cleanup
        perms.add(PosixFilePermission.OWNER_READ);
        Files.setPosixFilePermissions(inputDir, perms);

        // verify
        assertTrue(thrown.getMessage().contains("The option '-i' was configured with path '" + inputDir + "' which is not readable"));
    }

}
