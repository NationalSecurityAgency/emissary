package emissary.command.converter;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.hamcrest.junit.ExpectedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PathExistsReadableNoTrailingSlashConverterTest extends UnitTest {
    @Rule
    public ExpectedException expected = ExpectedException.none();
    private PathExistsReadableConverter converter;
    private Path path;

    @Before
    public void setup() throws IOException {
        path = Files.createTempDirectory("config");
        converter = new PathExistsReadableConverter("path");
    }

    @Test
    public void noTrailingSlash() {
        // test
        Path result = converter.convert(path.toString());

        // verify
        assertThat(result.endsWith("/"), equalTo(false));
    }

    @Test
    public void removeTrailingSlash() {
        // test
        Path result = converter.convert(path.toString() + "/");

        // verify
        assertThat(result.endsWith("/"), equalTo(false));
    }

    @Test(expected = RuntimeException.class)
    public void unreadablePath() throws Exception {
        // setup
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(path, perms);

        // test
        converter.convert(path.toString());

        // verify
        expected.expectMessage("The option 'path' was configured with path '" + path + "' which is not readable");

    }
}
