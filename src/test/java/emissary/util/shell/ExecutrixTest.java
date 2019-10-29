package emissary.util.shell;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExecutrixTest extends UnitTest {
    private Executrix e;
    private boolean isWindows = System.getProperty("os.name").indexOf("Window") != -1;

    @Override
    @Before
    public void setUp() throws Exception {
        this.e = new Executrix();

        final String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null || (tmp.indexOf("~") > -1 && this.isWindows)) {
            if (this.isWindows) {
                File f = new File("c:/tmp");
                if (f.exists() && f.isDirectory()) {
                    this.e.setTmpDir(f.getPath());
                } else {
                    f = new File("c:/temp");
                    if (f.exists() && f.isDirectory()) {
                        this.e.setTmpDir(f.getPath());
                    } else {
                        this.e.setTmpDir("/tmp");
                    }
                }
            } else {
                this.e.setTmpDir("/tmp");
            }
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.e = null;
        validateMockitoUsage();
    }

    @Test
    public void testExecutrixParams() {
        this.e.setInFileEnding(".in");
        this.e.setOutFileEnding(".out");
        this.e.setOrder("NORMAL");

        final String[] names = this.e.makeTempFilenames();
        assertNotNull("names array produced null", names);
        assertTrue("names has not enough elements", names.length >= Executrix.OUTPATH);
        assertNotNull("names array has null DIR", names[Executrix.DIR]);
        assertNotNull("names array has null BASE", names[Executrix.BASE]);
        assertNotNull("names array has null BASE_PATH", names[Executrix.BASE_PATH]);
        assertNotNull("names array has null IN", names[Executrix.IN]);
        assertNotNull("names array has null OUT", names[Executrix.OUT]);
        assertNotNull("names array has null INPATH", names[Executrix.INPATH]);
        assertNotNull("names array has null OUTPATH", names[Executrix.OUTPATH]);
        assertTrue("names must use out file ending", names[Executrix.OUT].endsWith(".out"));
        assertTrue("names must use in file ending", names[Executrix.IN].endsWith(".in"));
    }

    @Test
    public void testExecutrixUniqueBase() {
        this.e.setInFileEnding(".in");
        this.e.setOutFileEnding(".out");
        this.e.setOrder("NORMAL");

        final int COUNT = 1000;
        final Set<String> basePathSet = Collections.synchronizedSet(new HashSet<>(COUNT));

        // Generate COUNT sets of names
        IntStream.range(0, COUNT).parallel().forEach(number -> {
            final String[] name = this.e.makeTempFilenames();
            assertNotNull("name null DIR", name[Executrix.DIR]);
            assertNotNull("name null BASE", name[Executrix.BASE]);
            assertNotNull("name null BASE_PATH", name[Executrix.BASE_PATH]);
            basePathSet.add(name[Executrix.BASE_PATH]);
        });
        assertEquals("Some BASE_PATH entries mismatch", COUNT, basePathSet.size());
    }

    @Test
    public void testReadWrite() throws Exception {
        final String TMPDIR = this.e.getTmpDir();
        assertTrue("File written", Executrix.writeDataToFile("aaa".getBytes(), 0, 3, TMPDIR + "/foo.dat", false));
        byte[] data = Executrix.readFile(TMPDIR + "/foo.dat");
        assertNotNull("Data must be read", data);
        assertTrue("Data must all be read", data.length == 3);

        // append
        assertTrue("File written", Executrix.writeDataToFile("aaa".getBytes(), 0, 3, TMPDIR + "/foo.dat", true));
        data = Executrix.readFile(TMPDIR + "/foo.dat");
        assertNotNull("Data must be read", data);
        assertTrue("Data must all be read", data.length == 6);

        data = Executrix.readFile(TMPDIR + "/foo.dat", 3);
        assertNotNull("Data must be read", data);
        assertTrue("Data must be read up to limit", data.length == 3);

        data = Executrix.readFile(TMPDIR + "/foo.dat", 3000);
        assertNotNull("Data must be read", data);
        assertTrue("Data must be read up to actual size", data.length == 6);

        assertFalse("Should not write null data", Executrix.writeDataToFile((byte[]) null, TMPDIR + "/foo.dat"));
        assertFalse("Should not write null data", Executrix.writeDataToFile((byte[]) null, TMPDIR + "/foo.dat", false));
        assertFalse("Should not write null data", Executrix.writeDataToFile((byte[]) null, 0, 3, TMPDIR + "/foo.dat", false));

        assertTrue("Overwrite longer file should truncate previous data", Executrix.writeDataToFile("aaa".getBytes(), TMPDIR + "/foo.dat", false));
        data = Executrix.readFile(TMPDIR + "/foo.dat");
        assertNotNull("Data must be read", data);
        assertTrue("Data must be read up to actual size", data.length == 3);

        data = Executrix.readDataFromFile(TMPDIR + "/foo.dat");
        assertNotNull("Data must be read", data);
        assertTrue("Data must be read up to actual size", data.length == 3);

        data = Executrix.readDataFromFile(TMPDIR + "/filedoesnotexist.dat");
        assertNull("Read non existent does not throw", data);

        assertFalse("Write to null path", Executrix.writeDataToFile("aaa".getBytes(), null));
        assertFalse("Write to null path", Executrix.writeDataToFile("aaa".getBytes(), null, false));
        assertFalse("Write to null path", Executrix.writeDataToFile("aaa".getBytes(), 0, 3, null, false));

        RandomAccessFile raf = new RandomAccessFile(TMPDIR + "/foo.dat", "rw");
        data = Executrix.readDataFromFile(raf);
        assertNotNull("Data must be read from raf", data);
        assertTrue("Data must all be read from raf", data.length == 3);
        raf.close();

        raf = new RandomAccessFile(TMPDIR + "/foo.dat", "rw");
        data = Executrix.readDataFromFile(raf, 2, 1);
        assertNotNull("Data must be read from raf", data);
        assertTrue("Data must all be read from raf", data.length == 1);

        raf.seek(0);
        data = Executrix.readDataFromFile(raf, 100, 200);
        assertNull("Data requested out of bounds", data);

        raf.seek(0);
        data = Executrix.readDataFromFile(raf, 0, 100);
        assertNotNull("Available data read from raf", data);
        assertTrue("All available data read from raf", data.length == 3);

        raf.seek(0);
        final FileChannel channel = raf.getChannel();
        data = Executrix.readDataFromChannel(channel);
        assertNotNull("Available data read from channel", data);
        assertTrue("All available data read from channel", data.length == 3);

        data = Executrix.readDataFromChannel(channel, 0, 3);
        assertNotNull("Available data read from channel", data);
        assertTrue("All available data read from channel", data.length == 3);

        data = Executrix.readDataFromChannel(channel, 0, 1);
        assertNotNull("Available data read from channel", data);
        assertTrue("All limit data read from channel", data.length == 1);

        data = Executrix.readDataFromChannel(channel, 0, 100);
        assertNotNull("Available data read from channel", data);
        assertTrue("All limit data read from channel", data.length == 3);

        data = Executrix.readDataFromChannel(channel, 50, 100);
        assertNull("Out of bounds data on channel returned non-null", data);

        channel.close();
        raf.close();

        final File f = new File(TMPDIR + "/foo.dat");
        f.delete();
    }

    @Test
    public void testReadWriteTempDir() {
        String[] names = this.e.writeDataToNewTempDir("aaa".getBytes());
        assertNotNull("names on temp dir write", names);
        readAndNuke(names[Executrix.INPATH]);
        Executrix.cleanupDirectory(names[Executrix.DIR]);

        names = this.e.writeDataToNewTempDir("aaa".getBytes(), 0, 1);
        assertNotNull("names on temp dir write", names);
        readAndNuke(names[Executrix.INPATH]);
        Executrix.cleanupDirectory(names[Executrix.DIR]);
    }

    @Test
    public void testCopyFile() throws Exception {
        final String TMPDIR = this.e.getTmpDir();
        assertTrue("File written", Executrix.writeDataToFile("aaa".getBytes(), 0, 3, TMPDIR + "/foo.dat", false));
        Executrix.copyFile(TMPDIR + "/foo.dat", TMPDIR + "/bar.dat");
        final byte[] data = Executrix.readFile(TMPDIR + "/bar.dat");
        assertNotNull("Data read from copy", data);
        assertTrue("All data read from copy", data.length == 3);

        File f = new File(TMPDIR + "/foo.dat");
        f.delete();
        f = new File(TMPDIR + "/bar.dat");
        f.delete();
    }

    @Test
    public void testCleanupNonExistentDir() throws Exception {
        final File dirToDelete = mock(File.class);
        when(dirToDelete.exists()).thenReturn(false);

        final boolean result = Executrix.cleanupDirectory(dirToDelete);
        assertThat(result, is(true));
    }

    @Test
    public void testCleanupNonDir() throws Exception {
        final File dirToDelete = mock(File.class);
        when(dirToDelete.exists()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(dirToDelete.isFile()).thenReturn(true);
        when(dirToDelete.delete()).thenReturn(false).thenReturn(true);

        final boolean result = Executrix.cleanupDirectory(dirToDelete);
        assertThat(result, is(true));
    }

    @Test
    public void testCleanupIOProblemDir() throws Exception {
        final File dirToDelete = mock(File.class);
        when(dirToDelete.exists()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(dirToDelete.isFile()).thenReturn(false);
        when(dirToDelete.delete()).thenReturn(false).thenReturn(true);
        when(dirToDelete.listFiles()).thenReturn(null);

        final boolean result = Executrix.cleanupDirectory(dirToDelete);
        assertThat(result, is(false));
    }

    @Test
    public void testWriteWithCleanup() throws Exception {
        final String TMPDIR = this.e.getTmpDir();
        assertTrue("File Written in subdir", Executrix.writeDataToFile("abc".getBytes(), TMPDIR + "/foo/bar/baz.dat"));
        byte[] data = Executrix.readFile(TMPDIR + "/foo/bar/baz.dat");
        assertNotNull("Data read from subdir", data);
        assertTrue("All data read from subdir", data.length == 3);

        assertTrue("Cleanup removes all", Executrix.cleanupDirectory(TMPDIR + "/foo"));

        data = Executrix.readDataFromFile(TMPDIR + "/foo/bar/baz.dat");
        assertNull("Data read from non-existent subdir", data);
    }

    @Test
    public void testExecute() {

        // if (isWindows)
        // {
        // logger.debug("This test needs to be made to work on windoze");
        // return;
        // }
        final String[] names = this.e.makeTempFilenames();
        logger.debug("Names for testExecute is " + Arrays.asList(names));

        final File tdir = new File(names[Executrix.DIR]);
        tdir.mkdirs();
        assertTrue("Temp dir exists", tdir.exists() && tdir.isDirectory());

        assertTrue("File written", Executrix.writeDataToFile("aaa".getBytes(), names[Executrix.INPATH]));
        final byte[] data = Executrix.readDataFromFile(names[Executrix.INPATH]);
        assertNotNull("Data must be read from " + names[Executrix.INPATH], data);

        final String cyg = System.getProperty("CYGHOME");
        final boolean cyghome = cyg != null && cyg.indexOf(":") > -1;
        final String cmd = (this.isWindows ? (cyghome ? "/bin/cp" : "copy") : "cp") + " <INPUT_NAME> <OUTPUT_NAME>";
        String[] c = this.e.getCommand(cmd, names);
        assertNotNull("Command returned", c);
        assertEquals("Command runner", (this.isWindows ? "cmd" : "/bin/sh"), c[0]);

        this.e.setCommand(cmd);
        c = this.e.getCommand(names);
        assertNotNull("Command returned", c);
        assertEquals("Command runner", (this.isWindows ? "cmd" : "/bin/sh"), c[0]);

        logger.debug("Command to exec is " + Arrays.asList(c));

        int pstat = -1;
        final StringBuffer out = new StringBuffer();
        final StringBuffer err = new StringBuffer();

        pstat = this.e.execute(c, out, err);
        logger.debug("Stdout: " + out.toString());
        logger.debug("Stderr: " + err.toString());
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = this.e.execute(c, out);
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = this.e.execute(c, out, "UTF-8");
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = this.e.execute(c, out, err, "UTF-8");
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        final StringBuilder sout = new StringBuilder();
        final StringBuilder serr = new StringBuilder();

        pstat = this.e.execute(c, sout);
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = this.e.execute(c, sout, serr);
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = this.e.execute(c, sout, serr, "UTF-8");
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        final Map<String, String> env = new HashMap<String, String>();
        env.put("FOO", "BAR");

        pstat = this.e.execute(c, sout, serr, "UTF-8", env);
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        pstat = this.e.execute(c);
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        this.e.setProcessMaxMillis(0); // wait forever
        pstat = this.e.execute(c, sout, serr, "UTF-8", env);
        assertTrue("Process return value", pstat >= 0);
        readAndNuke(names[Executrix.OUTPATH]);

        assertTrue("Temp area clean up removes all", Executrix.cleanupDirectory(tdir));
        assertFalse("Temp area should be gone", tdir.exists());
    }

    private void readAndNuke(final String name) {
        final File f = new File(name);
        assertTrue("File " + name + " must exist", f.exists());
        final byte[] data = Executrix.readDataFromFile(name);
        assertNotNull("Data read from " + name + " was null", data);
        f.delete();
    }
}
