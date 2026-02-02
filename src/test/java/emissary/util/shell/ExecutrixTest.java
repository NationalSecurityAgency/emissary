package emissary.util.shell;

import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

class ExecutrixTest extends UnitTest {
    @Nullable
    private Executrix e;

    @BeforeEach
    public void setUp(@TempDir Path dir) throws Exception {
        e = new Executrix();
        if (System.getProperty("java.io.tmpdir") == null) {
            e.setTmpDir(dir.toString());
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        e = null;
        validateMockitoUsage();
    }

    @Test
    @SuppressWarnings("deprecation") // confirming functionality of legacy API
    void testExecutrixParams() {
        e.setInFileEnding(".in");
        e.setOutFileEnding(".out");
        e.setOrder("NORMAL");

        final String[] names = e.makeTempFilenames();
        assertNotNull(names, "names array produced null");
        assertTrue(names.length >= Executrix.OUTPATH, "names has not enough elements");
        assertNotNull(names[Executrix.DIR], "names array has null DIR");
        assertNotNull(names[Executrix.BASE], "names array has null BASE");
        assertNotNull(names[Executrix.BASE_PATH], "names array has null BASE_PATH");
        assertNotNull(names[Executrix.IN], "names array has null IN");
        assertNotNull(names[Executrix.OUT], "names array has null OUT");
        assertNotNull(names[Executrix.INPATH], "names array has null INPATH");
        assertNotNull(names[Executrix.OUTPATH], "names array has null OUTPATH");
        assertTrue(names[Executrix.OUT].endsWith(".out"), "names must use out file ending");
        assertTrue(names[Executrix.IN].endsWith(".in"), "names must use in file ending");
    }

    /**
     * Identical to {@link #testExecutrixParams}, but with the new TempFileNames API for constructing temp filenames
     */
    @Test
    void testExecutrixParamsWithNewTempFileNamesApi() {
        assertNotNull(e, "Executrix instance should not be null");
        e.setInFileEnding(".in");
        e.setOutFileEnding(".out");
        e.setOrder("NORMAL");

        final TempFileNames names = e.createTempFilenames();
        assertNotNull(names.getTempDir(), "getDir() returned null");
        assertNotNull(names.getBase(), "getBase() returned null");
        assertNotNull(names.getBasePath(), "getBasePath() returned null");
        assertNotNull(names.getIn(), "getIn() returned null");
        assertNotNull(names.getOut(), "getOut() returned null");
        assertNotNull(names.getInputFilename(), "getInPath() returned null");
        assertNotNull(names.getOutputFilename(), "getOutPath() returned null");
        assertTrue(names.getOut().endsWith(".out"), "output names must use out file ending");
        assertTrue(names.getIn().endsWith(".in"), "input names must use in file ending");
    }

    @Test
    @SuppressWarnings("deprecation") // confirming functionality of legacy API
    void testExecutrixUniqueBase() {
        e.setInFileEnding(".in");
        e.setOutFileEnding(".out");
        e.setOrder("NORMAL");

        final int COUNT = 1000;
        final Set<String> basePathSet = Collections.synchronizedSet(new HashSet<>(COUNT));

        // Generate COUNT sets of names
        IntStream.range(0, COUNT).parallel().forEach(number -> {
            final String[] name = e.makeTempFilenames();
            assertNotNull(name[Executrix.DIR], "name null DIR");
            assertNotNull(name[Executrix.BASE], "name null BASE");
            assertNotNull(name[Executrix.BASE_PATH], "name null BASE_PATH");
            basePathSet.add(name[Executrix.BASE_PATH]);
        });
        assertEquals(COUNT, basePathSet.size(), "Some BASE_PATH entries mismatch");
    }


    /**
     * Identical to {@link #testExecutrixUniqueBase}, but with the new TempFileNames API for constructing temp filenames
     */
    @Test
    void testExecutrixUniqueBaseWithTempFileNamesApi() {
        e.setInFileEnding(".in");
        e.setOutFileEnding(".out");
        e.setOrder("NORMAL");

        final int COUNT = 1000;
        final Set<String> basePathSet = Collections.synchronizedSet(new HashSet<>(COUNT));

        // Generate COUNT sets of names
        IntStream.range(0, COUNT).parallel().forEach(number -> {
            final TempFileNames names = e.createTempFilenames();
            assertNotNull(names.getTempDir(), "getDir() returned null");
            assertNotNull(names.getBase(), "getBase() returned null");
            assertNotNull(names.getBasePath(), "getBasePath() returned null");
            basePathSet.add(names.getBasePath());
        });
        assertEquals(COUNT, basePathSet.size(), "Some BASE_PATH entries mismatch");
    }

    @Test
    void testReadWrite() throws Exception {
        final String TMPDIR = e.getTmpDir();
        assertTrue(Executrix.writeDataToFile("aaa".getBytes(), 0, 3, TMPDIR + "/foo.dat", false), "File written");
        byte[] data = Executrix.readFile(TMPDIR + "/foo.dat");
        assertNotNull(data, "Data must be read");
        assertEquals(3, data.length, "Data must all be read");

        // append
        assertTrue(Executrix.writeDataToFile("aaa".getBytes(), 0, 3, TMPDIR + "/foo.dat", true), "File written");
        data = Executrix.readFile(TMPDIR + "/foo.dat");
        assertNotNull(data, "Data must be read");
        assertEquals(6, data.length, "Data must all be read");

        data = Executrix.readFile(TMPDIR + "/foo.dat", 3);
        assertNotNull(data, "Data must be read");
        assertEquals(3, data.length, "Data must be read up to limit");

        data = Executrix.readFile(TMPDIR + "/foo.dat", 3000);
        assertNotNull(data, "Data must be read");
        assertEquals(6, data.length, "Data must be read up to actual size");

        assertFalse(Executrix.writeDataToFile(null, TMPDIR + "/foo.dat"), "Should not write null data");
        assertFalse(Executrix.writeDataToFile(null, TMPDIR + "/foo.dat", false), "Should not write null data");
        assertFalse(Executrix.writeDataToFile(null, 0, 3, TMPDIR + "/foo.dat", false), "Should not write null data");

        assertTrue(Executrix.writeDataToFile("aaa".getBytes(), TMPDIR + "/foo.dat", false), "Overwrite longer file should truncate previous data");
        data = Executrix.readFile(TMPDIR + "/foo.dat");
        assertNotNull(data, "Data must be read");
        assertEquals(3, data.length, "Data must be read up to actual size");

        data = Executrix.readDataFromFile(TMPDIR + "/foo.dat");
        assertNotNull(data, "Data must be read");
        assertEquals(3, data.length, "Data must be read up to actual size");

        data = Executrix.readDataFromFile(TMPDIR + "/filedoesnotexist.dat");
        assertNull(data, "Read non existent does not throw");

        assertFalse(Executrix.writeDataToFile("aaa".getBytes(), null), "Write to null path");
        assertFalse(Executrix.writeDataToFile("aaa".getBytes(), null, false), "Write to null path");
        assertFalse(Executrix.writeDataToFile("aaa".getBytes(), 0, 3, null, false), "Write to null path");

        RandomAccessFile raf = new RandomAccessFile(TMPDIR + "/foo.dat", "rw");
        data = Executrix.readDataFromFile(raf);
        assertNotNull(data, "Data must be read from raf");
        assertEquals(3, data.length, "Data must all be read from raf");
        raf.close();

        raf = new RandomAccessFile(TMPDIR + "/foo.dat", "rw");
        data = Executrix.readDataFromFile(raf, 2, 1);
        assertNotNull(data, "Data must be read from raf");
        assertEquals(1, data.length, "Data must all be read from raf");

        raf.seek(0);
        data = Executrix.readDataFromFile(raf, 100, 200);
        assertNull(data, "Data requested out of bounds");

        raf.seek(0);
        data = Executrix.readDataFromFile(raf, 0, 100);
        assertNotNull(data, "Available data read from raf");
        assertEquals(3, data.length, "All available data read from raf");

        raf.seek(0);
        final FileChannel channel = raf.getChannel();
        data = Executrix.readDataFromChannel(channel);
        assertNotNull(data, "Available data read from channel");
        assertEquals(3, data.length, "All available data read from channel");

        data = Executrix.readDataFromChannel(channel, 0, 3);
        assertNotNull(data, "Available data read from channel");
        assertEquals(3, data.length, "All available data read from channel");

        data = Executrix.readDataFromChannel(channel, 0, 1);
        assertNotNull(data, "Available data read from channel");
        assertEquals(1, data.length, "All limit data read from channel");

        data = Executrix.readDataFromChannel(channel, 0, 100);
        assertNotNull(data, "Available data read from channel");
        assertEquals(3, data.length, "All limit data read from channel");

        data = Executrix.readDataFromChannel(channel, 50, 100);
        assertNull(data, "Out of bounds data on channel returned non-null");

        channel.close();
        raf.close();

        final File f = new File(TMPDIR + "/foo.dat");
        Files.deleteIfExists(f.toPath());
    }

    @Test
    void testReadWriteTempDir() throws IOException {
        String[] names = e.writeDataToNewTempDir("aaa".getBytes());
        assertNotNull(names, "names on temp dir write");
        readAndNuke(names[Executrix.INPATH]);
        Executrix.cleanupDirectory(names[Executrix.DIR]);

        names = e.writeDataToNewTempDir("aaa".getBytes(), 0, 1);
        assertNotNull(names, "names on temp dir write");
        readAndNuke(names[Executrix.INPATH]);
        Executrix.cleanupDirectory(names[Executrix.DIR]);
    }

    @Test
    void testCopyFile() throws Exception {
        final String TMPDIR = e.getTmpDir();
        try {
            assertTrue(Executrix.writeDataToFile("aaa".getBytes(), 0, 3, TMPDIR + "/foo.dat", false), "File written");
            Executrix.copyFile(TMPDIR + "/foo.dat", TMPDIR + "/bar.dat");
            final byte[] data = Executrix.readFile(TMPDIR + "/bar.dat");
            assertNotNull(data, "Data read from copy");
            assertEquals(3, data.length, "All data read from copy");
        } finally {
            Files.deleteIfExists(Paths.get(TMPDIR, "foo.dat"));
            Files.deleteIfExists(Paths.get(TMPDIR, "bar.dat"));
        }
    }

    @Test
    void testCleanupNonExistentDir() {
        final File dirToDelete = mock(File.class);
        when(dirToDelete.exists()).thenReturn(false);

        final boolean result = Executrix.cleanupDirectory(dirToDelete);
        assertTrue(result);
    }

    @Test
    void testCleanupNonDir() {
        final File dirToDelete = mock(File.class);
        when(dirToDelete.exists()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(dirToDelete.isFile()).thenReturn(true);
        when(dirToDelete.delete()).thenReturn(false).thenReturn(true);

        final boolean result = Executrix.cleanupDirectory(dirToDelete);
        assertTrue(result);
    }

    @Test
    void testCleanupIOProblemDir() {
        final File dirToDelete = mock(File.class);
        when(dirToDelete.exists()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(dirToDelete.isFile()).thenReturn(false);
        when(dirToDelete.delete()).thenReturn(false).thenReturn(true);
        when(dirToDelete.listFiles()).thenReturn(null);

        final boolean result = Executrix.cleanupDirectory(dirToDelete);
        assertFalse(result);
    }

    @Test
    void testWriteWithCleanup() throws Exception {
        final String TMPDIR = e.getTmpDir();
        assertTrue(Executrix.writeDataToFile("abc".getBytes(), TMPDIR + "/foo/bar/baz.dat"), "File Written in subdir");
        byte[] data = Executrix.readFile(TMPDIR + "/foo/bar/baz.dat");
        assertNotNull(data, "Data read from subdir");
        assertEquals(3, data.length, "All data read from subdir");

        assertTrue(Executrix.cleanupDirectory(TMPDIR + "/foo"), "Cleanup removes all");

        data = Executrix.readDataFromFile(TMPDIR + "/foo/bar/baz.dat");
        assertNull(data, "Data read from non-existent subdir");
    }

    @Test
    @SuppressWarnings("deprecation") // confirming functionality of legacy API
    void testExecute() throws IOException {

        final String[] names = e.makeTempFilenames();
        logger.debug("Names for testExecute is " + Arrays.asList(names));

        final File tdir = new File(names[Executrix.DIR]);
        Files.createDirectories(tdir.toPath());
        assertTrue(tdir.exists() && tdir.isDirectory(), "Temp dir exists");

        assertTrue(Executrix.writeDataToFile("aaa".getBytes(), names[Executrix.INPATH]), "File written");
        final byte[] data = Executrix.readDataFromFile(names[Executrix.INPATH]);
        assertNotNull(data, "Data must be read from " + names[Executrix.INPATH]);

        final String cmd = "cp <INPUT_NAME> <OUTPUT_NAME>";
        String[] c = e.getCommand(names, cmd);
        assertNotNull(c, "Command returned");
        assertEquals("/bin/sh", c[0], "Command runner");

        e.setCommand(cmd);
        c = e.getCommand(names);
        assertNotNull(c, "Command returned");
        assertEquals("/bin/sh", c[0], "Command runner");

        logger.debug("Command to exec is " + Arrays.asList(c));

        int pstat;
        final StringBuilder out = new StringBuilder();
        final StringBuilder err = new StringBuilder();

        pstat = e.execute(c, out, err);
        logger.debug("Stdout: " + out);
        logger.debug("Stderr: " + err);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = e.execute(c, out);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = e.execute(c, out, new StringBuilder("UTF-8"));
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = e.execute(c, out, err, "UTF-8");
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        final StringBuilder sout = new StringBuilder();
        final StringBuilder serr = new StringBuilder();

        pstat = e.execute(c, sout);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = e.execute(c, sout, serr);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = e.execute(c, sout, serr, "UTF-8");
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        final Map<String, String> env = new HashMap<>();
        env.put("FOO", "BAR");

        pstat = e.execute(c, sout, serr, "UTF-8", env);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = e.execute(c);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        e.setProcessMaxMillis(0); // wait forever
        pstat = e.execute(c, sout, serr, "UTF-8", env);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names[Executrix.OUTPATH]);

        assertTrue(Executrix.cleanupDirectory(tdir), "Temp area clean up removes all");
        assertFalse(tdir.exists(), "Temp area should be gone");
    }

    /**
     * Identical to {@link #testExecute}, but with the new TempFileNames API for constructing temp filenames
     */
    @Test
    void testExecuteWithTempFileNamesApi() throws IOException {
        assertNotNull(e, "Executrix instance should not be null");

        final TempFileNames names = e.createTempFilenames();
        logger.debug("Names for testExecute is {}", names);

        final File tdir = new File(names.getTempDir());
        Files.createDirectories(tdir.toPath());
        assertTrue(tdir.exists() && tdir.isDirectory(), "Temp dir exists");

        assertTrue(Executrix.writeDataToFile("aaa".getBytes(), names.getInputFilename()), "File written");
        final byte[] data = Executrix.readDataFromFile(names.getInputFilename());
        assertNotNull(data, "Data must be read from " + names.getInputFilename());

        final String cmd = "cp <INPUT_NAME> <OUTPUT_NAME>";
        String[] c = e.getCommand(names, cmd);
        assertNotNull(c, "Command returned");
        assertEquals("/bin/sh", c[0], "Command runner");

        e.setCommand(cmd);
        c = e.getCommand(names);
        assertNotNull(c, "Command returned");
        assertEquals("/bin/sh", c[0], "Command runner");

        logger.debug("Command to exec is {}", Arrays.asList(c));

        int pstat;
        final StringBuilder out = new StringBuilder();
        final StringBuilder err = new StringBuilder();

        pstat = e.execute(c, out, err);
        logger.debug("Stdout: {}", out);
        logger.debug("Stderr: {}", err);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        pstat = e.execute(c, out);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        pstat = e.execute(c, out, new StringBuilder("UTF-8"));
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        pstat = e.execute(c, out, err, "UTF-8");
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        final StringBuilder sout = new StringBuilder();
        final StringBuilder serr = new StringBuilder();

        pstat = e.execute(c, sout);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        pstat = e.execute(c, sout, serr);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        pstat = e.execute(c, sout, serr, "UTF-8");
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        final Map<String, String> env = new HashMap<>();
        env.put("FOO", "BAR");

        pstat = e.execute(c, sout, serr, "UTF-8", env);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        pstat = e.execute(c);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        e.setProcessMaxMillis(0); // wait forever
        pstat = e.execute(c, sout, serr, "UTF-8", env);
        assertTrue(pstat >= 0, "Process return value");
        readAndNuke(names.getOutputFilename());

        assertTrue(Executrix.cleanupDirectory(tdir), "Temp area clean up removes all");
        assertFalse(tdir.exists(), "Temp area should be gone");
    }

    @Test
    void testExecuteStream() {

        String expected = "bbb";
        final byte[] data = expected.getBytes();

        final String cmd = "/bin/cat";

        int pstat;
        final StringBuilder sout = new StringBuilder();
        final StringBuilder serr = new StringBuilder();

        pstat = e.execute(cmd, data);
        assertTrue(pstat >= 0, "Process return value");

        pstat = e.execute(cmd, "".getBytes(StandardCharsets.UTF_8), sout);
        assertTrue(pstat >= 0, "Process return value");
        assertEquals("", sout.toString());
        sout.setLength(0);

        pstat = e.execute(cmd, data, sout);
        assertTrue(pstat >= 0, "Process return value");
        assertEquals(expected, sout.toString().trim());
        sout.setLength(0);

        pstat = e.execute(cmd, data, sout, serr);
        assertTrue(pstat >= 0, "Process return value");
        assertTrue(sout.toString().startsWith(expected));
        assertEquals(expected, sout.toString().trim());
        sout.setLength(0);

        pstat = e.execute(cmd, data, sout, serr, "UTF-8");
        assertTrue(pstat >= 0, "Process return value");
        assertTrue(sout.toString().startsWith(expected));
        assertEquals(expected, sout.toString().trim());
        sout.setLength(0);

        final Map<String, String> env = new HashMap<>();
        env.put("FOO", "BAR");

        pstat = e.execute(new String[] {cmd}, data, sout, serr, "UTF-8", env);
        assertTrue(pstat >= 0, "Process return value");
        assertEquals(expected, sout.toString().trim());
        assertEquals("", serr.toString());
        sout.setLength(0);

        e.setProcessMaxMillis(0); // wait forever
        pstat = e.execute(new String[] {cmd}, data, sout, serr, "UTF-8", env);
        assertTrue(pstat >= 0, "Process return value");
        assertEquals("", serr.toString());
        assertEquals(expected, sout.toString().trim());
        sout.setLength(0);
    }

    @Test
    void testExecuteNoStream() {

        String expected = "ccc";
        final String[] cmd = {"/bin/echo", expected};

        int pstat;
        final StringBuilder sout = new StringBuilder();
        final StringBuilder serr = new StringBuilder();

        pstat = e.execute(cmd);
        assertTrue(pstat >= 0, "Process return value");

        pstat = e.execute(cmd, sout);
        assertTrue(pstat >= 0, "Process return value");
        assertEquals(expected, sout.toString().trim());
        sout.setLength(0);

        pstat = e.execute(cmd, sout, serr);
        assertTrue(pstat >= 0, "Process return value");
        assertEquals(expected, sout.toString().trim());
        assertEquals("", serr.toString());
        sout.setLength(0);

        pstat = e.execute(cmd, sout, serr, "UTF-8");
        assertTrue(pstat >= 0, "Process return value");
        assertEquals(expected, sout.toString().trim());
        assertEquals("", serr.toString());
        sout.setLength(0);
    }

    @Test
    void testExecuteGetBinary() {

        String expected = "ccc";
        final String[] cmd = {"/bin/echo", "-n", expected};

        int pstat;
        final ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
        final StringBuilder serr = new StringBuilder();

        pstat = e.execute(cmd, null, baosOut, serr);
        assertTrue(pstat >= 0, "Process return value");
        assertArrayEquals(expected.getBytes(), baosOut.toByteArray());
        assertEquals("", serr.toString());
        baosOut.reset();

        pstat = e.execute(cmd, null, baosOut, serr, "UTF-8", null);
        assertTrue(pstat >= 0, "Process return value");
        assertEquals(expected, baosOut.toString());
        assertEquals("", serr.toString());
        baosOut.reset();
    }

    @Test
    void testCleanPlaceName() {
        String expected = "PLACE_NAME_1";
        assertEquals(expected, Executrix.cleanPlaceName(expected));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE NAME 1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE(NAME)1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE/NAME/1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE.NAME.1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE#NAME$1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE\\NAME\\1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE\"NAME\"1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE;NAME'1"));
        assertEquals(expected, Executrix.cleanPlaceName("PLACE^NAME@1"));

        assertEquals("PLACE-NAME-1", Executrix.cleanPlaceName("PLACE-NAME-1"));
    }

    private static void readAndNuke(final String name) throws IOException {
        final File f = new File(name);
        assertTrue(f.exists(), "File " + name + " must exist");
        final byte[] data = Executrix.readDataFromFile(name);
        assertNotNull(data, "Data read from " + name + " was null");
        Files.deleteIfExists(f.toPath());
    }
}
