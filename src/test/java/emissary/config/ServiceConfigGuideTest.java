package emissary.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emissary.test.core.UnitTest;
import emissary.util.shell.Executrix;
import org.junit.Test;

public class ServiceConfigGuideTest extends UnitTest {

    private static final String cdata = "PLACE_NAME = TestPlace\n" + "SERVICE_NAME = TEST_PLACE\n" + "SERVICE_TYPE = \"INITIAL\"\n"
            + "SERVICE_DESCRIPTION = \"Test Place\"\n" + "SERVICE_COST = 50\n" + "SERVICE_QUALITY = 50\n" + "INITIAL_FORM = \"UNKNOWN\"\n"
            + "SERVICE_PROXY = \"TESTJUNK\"\n";

    private final InputStream cis = new ByteArrayInputStream(cdata.getBytes());

    @Test
    public void testInterface() {
        final ServiceConfigGuide s = new ServiceConfigGuide();
        assertTrue("Meets interface", s instanceof Configurator);
    }

    @Test
    public void testSingleReplacement() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'} b\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull("Parsed config data", entry);
        assertEquals("Replace single ENV entry", 5, entry.length());
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("Entry replacement value", "a / b", entry);
    }

    @Test
    public void testSingleReplacementAtBOL() throws IOException {
        final byte[] configData = "FOO = \"@ENV{'file.separator'} b\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull("Parsed config data", entry);
        assertEquals("Replace single ENV entry", 3, entry.length());
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("Entry replacement value", "/ b", entry);
    }

    @Test
    public void testSingleReplacementAtEOL() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'}\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull("Parsed config data", entry);
        assertEquals("Replace single ENV entry", 3, entry.length());
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("Entry replacement value", "a /", entry);
    }

    @Test
    public void testDoubleReplacement() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'}@ENV{'file.separator'} b\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull("Parsed config data", entry);
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("Entry replacement value", "a // b", entry);
    }

    @Test
    public void testAutomaticEnv() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'} b @{file.separator} c\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull("Parsed config data", entry);
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("Entry replacement value", "a / b / c", entry);
    }

    @Test
    public void testDynamicReplacement() throws IOException {
        final byte[] configData = "FOO = \"BAR\"\nQUUZ = \"@{FOO}\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        final String entry = c.findStringEntry("QUUZ");
        assertNotNull("Parsed config data", entry);
        assertEquals("All RHS values must be replaced", "BAR", entry);
    }

    @Test
    public void testBadQuoting() {
        final byte[] configData = "FOO = 123.123.123.123\nBAR = \"234.234.234.234\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        try {
            ConfigUtil.getConfigInfo(str);
            fail("Bad quoting should throw exception");
        } catch (IOException iox) {
            final String msg = iox.getMessage();
            assertTrue("Exception message must have line number", msg.indexOf("line 1?") > -1);
        }
    }

    @Test
    public void testAddSingleEntry() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        final List<String> entries = sc.findEntries("FOO");
        assertEquals("Only one entry may be returned", 1, entries.size());
        assertEquals("Added entry must be stored and retrieved", "BAR", entries.get(0));
    }

    @Test
    public void testAddMultipleEntries() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        final List<String> list = new ArrayList<String>();
        list.add("BAR");
        list.add("QUUZ");
        list.add("QAAX");
        sc.addEntries("FOO", list);
        final List<String> entries = sc.findEntries("FOO");
        assertEquals("All entries must be returned", list.size(), entries.size());
    }

    @Test
    public void testRemoveEntry() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "BAR");
        final List<String> entries = sc.findEntries("FOO");
        assertEquals("Removed entry may not be returned", 0, entries.size());
    }

    @Test
    public void testRemoveEntryFromMap() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "BAR");
        sc.addEntry("BLUB", "@{FOO}");
        final List<String> entries = sc.findEntries("BLUB");
        assertEquals("Removed entry may not be used in expansion", "@{FOO}", entries.get(0));
    }

    @Test
    public void testRemoveNoMatchEntry() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "QUUZ");
        final List<String> entries = sc.findEntries("FOO");
        assertEquals("Non-matching entry must not be removed", 1, entries.size());
    }

    @Test
    public void testRemoveNoMatchEntryFromMap() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "QUUZ");
        sc.addEntry("BLUB", "@{FOO}");
        final List<String> entries = sc.findEntries("BLUB");
        assertEquals("Entry must remain in map after non-matching remove", "BAR", entries.get(0));
    }

    @Test
    public void testRemoveAllLeavesMap() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.addEntry("FOO", "QUUZ");
        sc.addEntry("XYZ", "ZYX");
        sc.removeEntry("FOO", "*");
        final List<String> entries = sc.findEntries("XYZ");
        assertEquals("Entry must remain in map after remove all of other parameter", "ZYX", entries.get(0));
    }

    @Test
    public void testLHSReplacement() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.addEntry("@{FOO}", "QUUZ");
        final List<String> entries = sc.findEntries("BAR");
        assertEquals("Substitution must take place on LHS", 1, entries.size());
        assertEquals("Substitution must take place on LHS", "QUUZ", entries.get(0));
    }

    @Test
    public void testBadSpacing() {
        final byte[] configData = "FOO=123\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        try {
            ConfigUtil.getConfigInfo(str);
            fail("Bad spacing should throw exception");
        } catch (IOException iox) {
            final String msg = iox.getMessage();
            assertTrue("Exception message must have line number: " + msg, msg.indexOf("line 1?") > -1);
        }
    }

    @Test
    public void testStringMatchList() throws IOException {
        final byte[] configData =
                ("FOO_ONE = \"BAR ONE\"\n" + "FOO_ONE = \"BAR TWO\"\n" + "FOO_TWO = \"BAZ\"\n" + "FOO_THREE = \"SHAZAM\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);

        final List<ConfigEntry> list = c.findStringMatchList("FOO_");
        assertEquals("All entries not found", 4, list.size());
        boolean seenBar1 = false;
        boolean seenBar2 = false;
        for (final ConfigEntry e : list) {
            if ("ONE".equals(e.getKey()) && "BAR ONE".equals(e.getValue())) {
                seenBar1 = true;
            }
            if ("ONE".equals(e.getKey()) && "BAR TWO".equals(e.getValue())) {
                seenBar2 = true;
            }
        }
        assertTrue("Must preserve multiple values for same key", seenBar1 && seenBar2);
    }

    @Test
    public void testEntryManipulation() throws IOException {
        byte[] configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "FOO = \"SHAZAM\"\n").getBytes();
        ByteArrayInputStream str = new ByteArrayInputStream(configData);
        Configurator c = ConfigUtil.getConfigInfo(str);
        assertEquals("String entry finds first", "BAR", c.findStringEntry("FOO"));
        assertEquals("Last string entry", "SHAZAM", c.findLastStringEntry("FOO"));
        // String match as list and set
        List<ConfigEntry> list = c.findStringMatchEntries("FOO");
        assertNotNull("Entries in string match list", list);
        assertEquals("All entries in string match list", 3, list.size());
        Set<String> set = c.findEntriesAsSet("FOO");
        assertNotNull("Entries in string set", set);
        assertEquals("All entries in string match set", 3, set.size());
        assertTrue("Entry in set", set.contains("BAR"));
        assertTrue("Entry in set", set.contains("BAZ"));
        assertTrue("Entry in set", set.contains("SHAZAM"));

        // Use findEntries method
        List<String> slist = c.findEntries("FOO", "BAR");
        assertNotNull("Entries in list", slist);
        assertEquals("All entries in list", 3, list.size());
        slist = c.findEntries("BOGUS", "ZUB");
        assertNotNull("Entries in default list", slist);
        assertEquals("Default value on entry list", 1, slist.size());

        // Test some default stuff
        assertEquals("Get with default", "BAR", c.findStringEntry("FOO", "FUZ"));
        assertEquals("Get with default no val", "BUZ", c.findStringEntry("ZUB", "BUZ"));

        // Now with an entry removed
        configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "FOO = \"SHAZAM\"\n" + "FOO != \"SHAZAM\"\n").getBytes();
        str = new ByteArrayInputStream(configData);
        c = ConfigUtil.getConfigInfo(str);
        assertEquals("String entry finds first", "BAR", c.findStringEntry("FOO"));
        list = c.findStringMatchEntries("FOO");
        assertNotNull("Entries in string match list", list);
        assertEquals("All entries in string match list", 2, list.size());
        set = c.findEntriesAsSet("FOO");
        assertNotNull("Entries in string set", set);
        assertEquals("All entries in string match set", 2, set.size());
        assertTrue("Entry in set", set.contains("BAR"));
        assertTrue("Entry in set", set.contains("BAZ"));
        assertTrue("Entry not in set", !set.contains("SHAZAM"));

        // Now with all entries removed
        configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "FOO = \"SHAZAM\"\n" + "FOO != \"*\"\n").getBytes();
        str = new ByteArrayInputStream(configData);
        c = ConfigUtil.getConfigInfo(str);
        assertNull("No entry for string match", c.findStringEntry("FOO"));
        list = c.findStringMatchEntries("FOO");
        assertNotNull("Entries in string match list", list);
        assertEquals("All entries in string match list", 0, list.size());
        set = c.findEntriesAsSet("FOO");
        assertNotNull("Entries in string set", set);
        assertEquals("All entries in string match set", 0, set.size());
        assertTrue("Entry not in set", !set.contains("BAR"));
        assertTrue("Entry not in set", !set.contains("BAZ"));
        assertTrue("Entry not in set", !set.contains("SHAZAM"));

        // Test out the mapping methods
        configData = ("FOO_ONE = \"BAR\"\n" + "FOO_TWO = \"BAZ\"\n" + "FOO_THREE = \"SHAZAM\"\n" + "FOO_four = \"blaze\"\n").getBytes();
        str = new ByteArrayInputStream(configData);
        c = ConfigUtil.getConfigInfo(str);
        assertEquals("Get entry", "BAR", c.findStringEntry("FOO_ONE"));
        Map<String, String> map = c.findStringMatchMap("FOO_");
        assertNotNull("Mapped entries", map);
        assertEquals("Mapped all entries", 4, map.size());
        assertEquals("Mapped entry one", "BAR", map.get("ONE"));
        assertEquals("Mapped entry two", "BAZ", map.get("TWO"));
        assertEquals("Mapped entry three", "SHAZAM", map.get("THREE"));
        assertEquals("Mapped entry four", "blaze", map.get("FOUR"));
        assertTrue("Mapped case not preserved", !map.containsKey("four"));

        // preserve case this time
        map = c.findStringMatchMap("FOO_", true);
        assertNotNull("Mapped entries", map);
        assertEquals("Mapped all entries", 4, map.size());
        assertEquals("Mapped entry one", "BAR", map.get("ONE"));
        assertEquals("Mapped entry two", "BAZ", map.get("TWO"));
        assertEquals("Mapped entry three", "SHAZAM", map.get("THREE"));
        assertEquals("Mapped entry four", "blaze", map.get("four"));
        assertTrue("Mapped entry not upper cased", !map.containsKey("FOUR"));

        // Try a nonexistent key
        map = c.findStringMatchMap("SHLOP_");
        assertNotNull("Map created for non existent key", map);
        assertEquals("Empty map", 0, map.size());
        map = c.findStringMatchMap(null);
        assertNotNull("Map created for non existent key", map);
        assertEquals("Empty map", 0, map.size());

    }

    @Test
    public void testEntryPrimitives() throws IOException {
        final byte[] configData =
                ("INTEGER = \"123\"\n" + "LONG = \"123456\"\n" + "DOUBLE = \"12345678\"\n" + "BOOLEANT = \"TRUE\"\n" + "BOOLEANF = \"FALSE\"\n"
                        + "BOOLEANQ = \"BOGUS\"\n" + "SZP = \"123\"\n" + "SZB = \"123b\"\n" + "SZK = \"123k\"\n" + "SZM = \"123m\"\n"
                        + "SZG = \"123g\"\n" + "SZT = \"123t\"\n" + "SZQ = \"red balloons\"\n" + "SZ1 = \"111\"\n" + "SZ2 = \"222\"\n"
                        + "SZ3 = \"333\"\n" + "SZ4 = \"444\"\n" + "SZ5 = \"555\"\n" + "SZ6 = \"666\"\n" + "SZ7 = \"777\"\n" + "SZ8 = \"888\"\n"
                        + "SZ9 = \"999\"\n" + "SZ0 = \"000\"\n" + "STRING = \"chars\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        assertEquals("Int entry with def", 123, c.findIntEntry("INTEGER", 456));
        assertEquals("Int entry no val", 456, c.findIntEntry("BLUBBER", 456));

        assertEquals("Long entry with def", 123456L, c.findLongEntry("LONG", 456L));
        assertEquals("Long entry no val", 456L, c.findLongEntry("BLUBBER", 456L));

        final double d = 12345678;
        final double d2 = 456;
        assertEquals(d, c.findDoubleEntry("DOUBLE", d2), 0d);
        assertEquals(d2, c.findDoubleEntry("BLUBBER", d2), 0d);

        assertTrue("Boolean entry", c.findBooleanEntry("BOOLEANT", false));
        assertTrue("Boolean entry", !c.findBooleanEntry("BOOLEANF", true));
        assertTrue("Boolean entry", c.findBooleanEntry("BLUBBER", true));
        assertTrue("Boolean entry", !c.findBooleanEntry("BLUBBER", false));
        assertTrue("Boolean entry", c.findBooleanEntry("BLUBBER", "true"));
        assertTrue("Boolean entry", !c.findBooleanEntry("BLUBBER", "false"));
        assertTrue("Boolean bad entry", c.findBooleanEntry("BOOLEANQ", true));
        assertFalse("Boolean bad entry", c.findBooleanEntry("BOOLEANQ", false));

        assertEquals("Size default tag", 666L, c.findSizeEntry("SZXYZ", 666L));
        assertEquals("Size bytes no marker", 123L, c.findSizeEntry("SZP", -1L));
        assertEquals("Size bytes", 123L, c.findSizeEntry("SZB", -1L));
        assertEquals("Size kbytes", 123L * 1024, c.findSizeEntry("SZK", -1L));
        assertEquals("Size mbytes", 123L * 1024 * 1024, c.findSizeEntry("SZM", -1L));
        assertEquals("Size gbytes", 123L * 1024 * 1024 * 1024, c.findSizeEntry("SZG", -1L));
        assertEquals("Size tbytes", 123L * 1024 * 1024 * 1024 * 1024, c.findSizeEntry("SZT", -1L));
        assertEquals("Size default", 123L, c.findSizeEntry("SZQ", 123L));
        assertEquals("Size digit check", 111L, c.findSizeEntry("SZ1", -1L));
        assertEquals("Size digit check", 222L, c.findSizeEntry("SZ2", -1L));
        assertEquals("Size digit check", 333L, c.findSizeEntry("SZ3", -1L));
        assertEquals("Size digit check", 444L, c.findSizeEntry("SZ4", -1L));
        assertEquals("Size digit check", 555L, c.findSizeEntry("SZ5", -1L));
        assertEquals("Size digit check", 666L, c.findSizeEntry("SZ6", -1L));
        assertEquals("Size digit check", 777L, c.findSizeEntry("SZ7", -1L));
        assertEquals("Size digit check", 888L, c.findSizeEntry("SZ8", -1L));
        assertEquals("Size digit check", 999L, c.findSizeEntry("SZ9", -1L));
        assertEquals("Size digit check", 0L, c.findSizeEntry("SZ0", -1L));
    }

    @Test
    public void testUTF8Chars() throws IOException {
        final String[] s = {
                "FOO = \"This is a wa\\\\ufb04e test \\\\xyz123 123\"\n", "FOO = \"This is a wa\\\\ufb04\"\n", "FOO = \"This is a \\\\u1D504\"\n",
                "FOO = \"This is a \\\\U1D504\"\n"};

        final StringBuilder extValue = new StringBuilder();
        extValue.append("This is a ").appendCodePoint(0x1D504);

        final String[] t = {"This is a wa\ufb04e test \\xyz123 123", "This is a wa\ufb04", extValue.toString(), extValue.toString()};

        for (int i = 0; i < s.length; i++) {
            final ServiceConfigGuide sc = new ServiceConfigGuide(new ByteArrayInputStream(s[i].getBytes()));
            assertEquals("UTF8 entry conversion failed in slot " + i, t[i], sc.findStringEntry("FOO", null));
        }
    }

    @Test
    public void testCreateDirAndFile() throws IOException {
        final String tmp = System.getProperty("java.io.tmpdir");
        final File tdir = new File(tmp, "foo-dir");
        final File tfile = new File(tdir, "foo-file.txt");
        final File t2file = new File(tdir.getPath() + "/subdir/blubdir/bar-file.txt");
        String s =
                "CREATE_DIRECTORY = \"" + tdir.getPath() + "\"\n" + "CREATE_FILE = \"" + tfile.getPath() + "\"\n" + "CREATE_FILE = \""
                        + tfile.getPath() + "\"\n" + "CREATE_FILE = \"" + t2file.getPath() + "\"\n";
        s = s.replace('\\', '/');

        new ServiceConfigGuide(new ByteArrayInputStream(s.getBytes()));
        assertTrue("Directory creation failed for " + tdir.getPath() + " in " + s, tdir.exists());
        assertTrue("File creation failed for " + tfile.getPath() + " in " + s, tfile.exists() && tfile.canRead());
        assertTrue("File and directory creation failed for " + t2file.getPath() + " in " + s, t2file.exists() && t2file.canRead());

        if (t2file.exists()) {
            t2file.deleteOnExit();
        }
        if (tfile.exists()) {
            tfile.deleteOnExit();
        }
        if (tdir.exists()) {
            tdir.deleteOnExit();
        }
    }

    @Test
    public void testMagicSubstitutions() throws IOException {
        final String s =
                "TGT_HOST = \"MYHOST\"\n" + "TGT_DOMAIN = \"MYDOMAIN\"\n" + "TGT_PORT = \"9999\"\n" + "DEBUG = \"true\"\n" + "MYHOST = \"@{HOST}\"\n"
                        + "MYPROJ = \"@{PRJ_BASE}/bin\"\n" + "MYTMP = \"@{TMPDIR}\"\n" + "MYBOGUS = \"@{HEREITIS}\"\n"
                        + "MYURL = \"http://@{TGT_HOST}.@{TGT_DOMAIN}:@{TGT_PORT}/\"\n" + "MYLIB = \"thelib-@{OS.NAME}-@{OS.VER}-@{OS.ARCH}.so\"\n"
                        + "MYCFG = \"@{CONFIG_DIR}@{/}TheStuff.cfg\"\n";
        final ServiceConfigGuide sc = new ServiceConfigGuide(new ByteArrayInputStream(s.getBytes()));

        assertEquals("Replacement of bogus key is unexpected", "@{HEREITIS}", sc.findStringEntry("MYBOGUS"));

        assertTrue("Replacement of  PRJ_BASE with value failed", sc.findStringEntry("MYPROJ").indexOf("PRJ_BASE") == -1);

        assertEquals("TMP_DIR magic replacement failed", System.getProperty("java.io.tmpdir"), sc.findStringEntry("MYTMP"));

        assertTrue("Replacement of magic HOST failed", sc.findStringEntry("MYHOST").indexOf("@") == -1);

        assertEquals("MYURL constructed from replacements", "http://MYHOST.MYDOMAIN:9999/", sc.findStringEntry("MYURL"));

        assertEquals("MYLIB construction from os replacements failed", "thelib-" + System.getProperty("os.name").replace(' ', '_') + "-"
                + System.getProperty("os.version").replace(' ', '_') + "-" + System.getProperty("os.arch").replace(' ', '_') + ".so",
                sc.findStringEntry("MYLIB"));

        assertEquals("MYCFG dir construction from path replacements failed", System.getProperty("emissary.config.dir", "/").replace('\\', '/')
                + "/TheStuff.cfg", sc.findStringEntry("MYCFG").replace('\\', '/'));

        assertTrue("Debug enable on config file failed", sc.debug());
    }


    @Test
    public void testConstructors() throws IOException {
        this.cis.reset();
        final ServiceConfigGuide sc = new ServiceConfigGuide(this.cis);
        final String pn = sc.findStringEntry("INITIAL_FORM");
        assertNotNull("Value from stream", pn);
        this.cis.reset();
        final ServiceConfigGuide sc2 = new ServiceConfigGuide(this.cis, "TestStream");
        assertEquals("Value from extraction", pn, sc2.findStringEntry("INITIAL_FORM"));

        // Write the config bytes out to a temp file
        final File scfile = File.createTempFile("temp", ".cfg");
        scfile.deleteOnExit();
        final FileOutputStream os = new FileOutputStream(scfile);
        os.write(cdata.getBytes());
        os.close();

        final ServiceConfigGuide sc3 = new ServiceConfigGuide(scfile.getAbsolutePath());
        assertEquals("Read file from disk", pn, sc3.findStringEntry("INITIAL_FORM"));
        final ServiceConfigGuide sc4 = new ServiceConfigGuide(scfile.getParent(), scfile.getName());
        assertEquals("Read dir and file from disk", pn, sc4.findStringEntry("INITIAL_FORM"));

    }

    @Test
    public void testMerge() throws IOException {
        final byte[] configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "KEY1 = \"VAL1\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c1 = ConfigUtil.getConfigInfo(str);
        final byte[] configData2 = ("FOO = \"BAR2\"\n" + "KEY2 = \"VAL2\"\n").getBytes();
        final ByteArrayInputStream str2 = new ByteArrayInputStream(configData2);
        final Configurator c2 = ConfigUtil.getConfigInfo(str2);

        c1.merge(c2);
        assertEquals("Newly merged entry is first", "BAR2", c1.findStringEntry("FOO"));
        assertEquals("Merge contains union of values", 3, c1.findEntries("FOO").size());
        assertEquals("Old value present after merge", "VAL1", c1.findStringEntry("KEY1"));
        assertEquals("New value present after merge", "VAL2", c1.findStringEntry("KEY2"));
    }

    @Test
    public void testMergeRemoval() throws IOException {
        final byte[] configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "KEY1 = \"VAL1\"\n" + "KEY1 = \"VAL2\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c1 = ConfigUtil.getConfigInfo(str);
        final byte[] configData2 = ("FOO != \"*\"\n" + "FOO = \"ZUB\"\n" + "KEY1 != \"VAL2\"\n").getBytes();
        final ByteArrayInputStream str2 = new ByteArrayInputStream(configData2);
        final Configurator c2 = ConfigUtil.getConfigInfo(str2);

        c1.merge(c2);

        assertEquals("Old values removed on *", 1, c1.findEntries("FOO").size());
        assertEquals("Specified value removed when listed", 1, c1.findEntries("KEY1").size());
        assertEquals("Non-specified value remains after remove", "VAL1", c1.findStringEntry("KEY1"));
    }

    @Test
    public void testOptImportWhenOptionalFileExists() throws IOException {
        // Write the config bytes out to a temp file
        final String dir = System.getProperty("java.io.tmpdir");
        final String priname = dir + "/primary.cfg";
        final String optname = dir + "/optional.cfg";

        final byte[] primary = new String("FOO = \"BAR\"\nOPT_IMPORT_FILE = \"" + optname + "\"\n").getBytes();
        final byte[] optional = new String("FOO = \"BAR2\"\n").getBytes();

        final Configurator c;
        try {
            Executrix.writeDataToFile(primary, priname);
            Executrix.writeDataToFile(optional, optname);
            c = new ServiceConfigGuide(priname);
        } finally {
            new File(priname).delete();
            new File(optname).delete();
        }


        assertEquals("Optional value present", 2, c.findEntries("FOO").size());
        assertEquals("Optional value present after primary", "BAR2", c.findEntries("FOO").get(1));
    }

    @Test
    public void testOptImportWhenOptionalFileDoesNotExist() throws IOException {
        // Write the config bytes out to a temp file
        final String dir = System.getProperty("java.io.tmpdir");
        final String priname = dir + "/primary.cfg";
        final byte[] primary = new String("FOO = \"BAR\"\nOPT_IMPORT_FILE = \"/tmp/bogus.cfg\"\n").getBytes();

        final Configurator c;
        try {
            Executrix.writeDataToFile(primary, priname);
            c = new ServiceConfigGuide(priname);
        } finally {
            new File(priname).delete();
        }
        assertEquals("Optional value not present", 1, c.findEntries("FOO").size());
    }


    @Test
    public void testOptImportWhenOptionalFileExistsButHasBadSyntax() {
        // Write the config bytes out to a temp file
        final String dir = System.getProperty("java.io.tmpdir");
        final String priname = dir + "/primary.cfg";
        final String optname = dir + "/optional.cfg";

        final byte[] primary = new String("FOO = \"BAR\"\nOPT_IMPORT_FILE = \"" + optname + "\"\n").getBytes();
        final byte[] optional = new String("FOO = \"BAR2\"\"\nBAD = \"LINE\"").getBytes();
        try {
            Executrix.writeDataToFile(primary, priname);
            Executrix.writeDataToFile(optional, optname);
            new ServiceConfigGuide(priname);
            fail("File parsing on OPT_IMPORT_FILE must fail when it has bad syntax");
        } catch (IOException iox) {
            // expected
        } finally {
            new File(priname).delete();
            new File(optname).delete();
        }

    }

    @Test
    public void testImportFromClasspath() throws IOException {
        final Configurator c = ConfigUtil.getConfigInfo("emissary.config.ServiceConfigGuideImportTest.cfg");
        assertEquals("Values from original and import present", 2, c.findEntries("FOO").size());
    }

    /**
     * This test case validates the patch for Emissary #201: https://github.com/NationalSecurityAgency/emissary/issues/201.
     *
     * It verifies that findStringEntry() returns the first non-null configured value or the provided default value if no
     * non-null values are configured for a property.
     */
    @Test
    public void testFindStringEntry_WithDefaultAndConfiguredNull_Emissary201() {
        final String unconfiguredProp = "test.unconfigured";
        final String nullProp = "test.nullProp";
        final String multiProp = "test.multiProp";
        final String multiNullProp = "test.multiNullProp";

        final String nonNullValue = "non-null value";
        final String defaultValue = "default value";

        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry(nullProp, null);
        config.addEntry(multiProp, null);
        config.addEntry(multiProp, null);
        config.addEntry(multiProp, nonNullValue);
        config.addEntry(multiProp, null);
        config.addEntry(multiNullProp, null);
        config.addEntry(multiNullProp, null);
        config.addEntry(multiNullProp, null);
        config.addEntry(multiNullProp, null);
        config.addEntry(multiNullProp, null);

        final String unconfiguredValue = config.findStringEntry(unconfiguredProp, defaultValue);
        final String nullValue = config.findStringEntry(nullProp, defaultValue);
        final String multiValue = config.findStringEntry(multiProp, defaultValue);
        final String multiNullValue = config.findStringEntry(multiNullProp, defaultValue);

        assertEquals("Unconfigured property should return default", defaultValue, unconfiguredValue);
        assertEquals("Property set to null should return default", defaultValue, nullValue);
        assertEquals("Multi-valued property should return first non-null value", nonNullValue, multiValue);
        assertEquals("Multi-valued property with all nulls should return default", defaultValue, multiNullValue);
    }
}
