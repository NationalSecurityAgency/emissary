package emissary.output.filter;

import emissary.test.core.UnitTest;
import emissary.util.UnitTestSecurityManager;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static emissary.util.io.UnitTestFileUtils.cleanupDirectoryRecursively;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KryoBDOFilterTest extends UnitTest {

    private final static String TEST_OUTPUT = "kryo-filter-test";
    private final static String TEST_ERROR = "kryo-error";
    private final static String TEST_INPUT = "kryo";
    private final static String TEST_CREATE_DIR = "create-dir";
    private Path outputDir;
    private Path inputDir;
    private Path errorDir;
    private Path createDir;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        outputDir = Files.createTempDirectory(TEST_OUTPUT);
        errorDir = Files.createTempDirectory(TEST_ERROR);
        inputDir = Files.createTempDirectory(TEST_INPUT);
        createDir = Files.createTempDirectory(TEST_CREATE_DIR);
        System.setSecurityManager(new UnitTestSecurityManager());
    }

    @Override
    public void tearDown() throws Exception
    {
        System.setSecurityManager(null);
        cleanupDirectoryRecursively(errorDir);
        cleanupDirectoryRecursively(inputDir);
        cleanupDirectoryRecursively(outputDir);
        cleanupDirectoryRecursively(createDir);
        super.tearDown();
    }

    @Test
    public void testValidateSuccess() throws IOException {
        KryoBDOFilter filter = new KryoBDOFilter();
        filter.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateNullFS() throws IOException {
        // Validate is called within the constructor
        KryoBDOFilter filter = new KryoBDOFilter();
        filter.fs = null;
        filter.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateNullBucket() throws IOException {
        // Validate is called within the constructor
        KryoBDOFilter filter = new KryoBDOFilter();
        filter.kryoBucket = null;
        filter.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateNullUtil() throws IOException {
        // Validate is called within the constructor
        KryoBDOFilter filter = new KryoBDOFilter();
        filter.dropOffUtil = null;
        filter.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateEmptyFilters() throws IOException {
        // Validate is called within the constructor
        KryoBDOFilter filter = new KryoBDOFilter();
        filter.filters = Collections.emptyList();
        filter.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateNullOutput() throws IOException {
        // Validate is called within the constructor
        KryoBDOFilter filter = new KryoBDOFilter();
        filter.outputPath = null;
        filter.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateNullError() throws IOException {
        // Validate is called within the constructor
        KryoBDOFilter filter = new KryoBDOFilter();
        filter.errorPath = null;
        filter.validate();
    }


    @Test
    public void testCreateBadDirectories() throws IOException {
        KryoBDOFilter filter = new KryoBDOFilter();
        try {
            filter.createDirectories(Paths.get("/really-bad-dir"));
        } catch (SecurityException se) {
            assertTrue(se.getMessage().equals(UnitTestSecurityManager.SYSTEM_EXIT));
            return;
        }
        fail("Security exception should be thrown for the System.exit");
    }

    @Test
    public void testCreateDirectories() throws IOException {
        KryoBDOFilter filter = new KryoBDOFilter();
        String tempDir = createDir.toAbsolutePath().toString() + "/bad";
        filter.createDirectories(Paths.get(tempDir));
        assertTrue(Files.exists(Paths.get(tempDir)));
    }
}
