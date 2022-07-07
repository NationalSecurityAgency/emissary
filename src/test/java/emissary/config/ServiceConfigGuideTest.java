package emissary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emissary.test.core.UnitTest;
import emissary.util.shell.Executrix;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class ServiceConfigGuideTest extends UnitTest {

    private static final String cdata = "PLACE_NAME = TestPlace\n" + "SERVICE_NAME = TEST_PLACE\n" + "SERVICE_TYPE = \"INITIAL\"\n"
            + "SERVICE_DESCRIPTION = \"Test Place\"\n" + "SERVICE_COST = 50\n" + "SERVICE_QUALITY = 50\n" + "INITIAL_FORM = \"UNKNOWN\"\n"
            + "SERVICE_PROXY = \"TESTJUNK\"\n";

    private final InputStream cis = new ByteArrayInputStream(cdata.getBytes());

    @Test
    void testInterface() {
        final ServiceConfigGuide s = new ServiceConfigGuide();
        assertTrue(s instanceof Configurator, "Meets interface");
    }

    @Test
    void testSingleReplacement() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'} b\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull(entry, "Parsed config data");
        assertEquals(5, entry.length(), "Replace single ENV entry");
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("a / b", entry, "Entry replacement value");
    }

    @Test
    void testSingleReplacementAtBOL() throws IOException {
        final byte[] configData = "FOO = \"@ENV{'file.separator'} b\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull(entry, "Parsed config data");
        assertEquals(3, entry.length(), "Replace single ENV entry");
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("/ b", entry, "Entry replacement value");
    }

    @Test
    void testSingleReplacementAtEOL() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'}\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull(entry, "Parsed config data");
        assertEquals(3, entry.length(), "Replace single ENV entry");
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("a /", entry, "Entry replacement value");
    }

    @Test
    void testDoubleReplacement() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'}@ENV{'file.separator'} b\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull(entry, "Parsed config data");
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("a // b", entry, "Entry replacement value");
    }

    @Test
    void testAutomaticEnv() throws IOException {
        final byte[] configData = "FOO = \"a @ENV{'file.separator'} b @{file.separator} c\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        String entry = c.findStringEntry("FOO");
        assertNotNull(entry, "Parsed config data");
        entry = entry.replace('\\', '/'); // make it look the same on all platforms
        assertEquals("a / b / c", entry, "Entry replacement value");
    }

    @Test
    void testDynamicReplacement() throws IOException {
        final byte[] configData = "FOO = \"BAR\"\nQUUZ = \"@{FOO}\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        final String entry = c.findStringEntry("QUUZ");
        assertNotNull(entry, "Parsed config data");
        assertEquals("BAR", entry, "All RHS values must be replaced");
    }

    @Test
    void testBadQuoting() {
        final byte[] configData = "FOO = 123.123.123.123\nBAR = \"234.234.234.234\"\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        IOException iox = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(str));
        final String msg = iox.getMessage();
        assertTrue(msg.contains("line 1?"), "Exception message must have line number");
    }

    @Test
    void testAddSingleEntry() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        final List<String> entries = sc.findEntries("FOO");
        assertEquals(1, entries.size(), "Only one entry may be returned");
        assertEquals("BAR", entries.get(0), "Added entry must be stored and retrieved");
    }

    @Test
    void testAddMultipleEntries() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        final List<String> list = new ArrayList<>();
        list.add("BAR");
        list.add("QUUZ");
        list.add("QAAX");
        sc.addEntries("FOO", list);
        final List<String> entries = sc.findEntries("FOO");
        assertEquals(list.size(), entries.size(), "All entries must be returned");
    }

    @Test
    void testRemoveEntry() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "BAR");
        final List<String> entries = sc.findEntries("FOO");
        assertEquals(0, entries.size(), "Removed entry may not be returned");
    }

    @Test
    void testRemoveEntryFromMap() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "BAR");
        sc.addEntry("BLUB", "@{FOO}");
        final List<String> entries = sc.findEntries("BLUB");
        assertEquals("@{FOO}", entries.get(0), "Removed entry may not be used in expansion");
    }

    @Test
    void testRemoveNoMatchEntry() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "QUUZ");
        final List<String> entries = sc.findEntries("FOO");
        assertEquals(1, entries.size(), "Non-matching entry must not be removed");
    }

    @Test
    void testRemoveNoMatchEntryFromMap() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.removeEntry("FOO", "QUUZ");
        sc.addEntry("BLUB", "@{FOO}");
        final List<String> entries = sc.findEntries("BLUB");
        assertEquals("BAR", entries.get(0), "Entry must remain in map after non-matching remove");
    }

    @Test
    void testRemoveAllLeavesMap() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.addEntry("FOO", "QUUZ");
        sc.addEntry("XYZ", "ZYX");
        sc.removeEntry("FOO", "*");
        final List<String> entries = sc.findEntries("XYZ");
        assertEquals("ZYX", entries.get(0), "Entry must remain in map after remove all of other parameter");
    }

    @Test
    void testLHSReplacement() {
        final ServiceConfigGuide sc = new ServiceConfigGuide();
        sc.addEntry("FOO", "BAR");
        sc.addEntry("@{FOO}", "QUUZ");
        final List<String> entries = sc.findEntries("BAR");
        assertEquals(1, entries.size(), "Substitution must take place on LHS");
        assertEquals("QUUZ", entries.get(0), "Substitution must take place on LHS");
    }

    @Test
    void testBadSpacing() {
        final byte[] configData = "FOO=123\n".getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        IOException iox = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(str));
        final String msg = iox.getMessage();
        assertTrue(msg.contains("line 1?"), "Exception message must have line number: " + msg);
    }

    @Test
    void testStringMatchList() throws IOException {
        final byte[] configData =
                ("FOO_ONE = \"BAR ONE\"\n" + "FOO_ONE = \"BAR TWO\"\n" + "FOO_TWO = \"BAZ\"\n" + "FOO_THREE = \"SHAZAM\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);

        final List<ConfigEntry> list = c.findStringMatchList("FOO_");
        assertEquals(4, list.size(), "All entries not found");
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
        assertTrue(seenBar1 && seenBar2, "Must preserve multiple values for same key");
    }

    @Test
    void testEntryManipulation() throws IOException {
        byte[] configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "FOO = \"SHAZAM\"\n").getBytes();
        ByteArrayInputStream str = new ByteArrayInputStream(configData);
        Configurator c = ConfigUtil.getConfigInfo(str);
        assertEquals("BAR", c.findStringEntry("FOO"), "String entry finds first");
        assertEquals("SHAZAM", c.findLastStringEntry("FOO"), "Last string entry");
        // String match as list and set
        List<ConfigEntry> list = c.findStringMatchEntries("FOO");
        assertNotNull(list, "Entries in string match list");
        assertEquals(3, list.size(), "All entries in string match list");
        Set<String> set = c.findEntriesAsSet("FOO");
        assertNotNull(set, "Entries in string set");
        assertEquals(3, set.size(), "All entries in string match set");
        assertTrue(set.contains("BAR"), "Entry in set");
        assertTrue(set.contains("BAZ"), "Entry in set");
        assertTrue(set.contains("SHAZAM"), "Entry in set");

        // Use findEntries method
        List<String> slist = c.findEntries("FOO", "BAR");
        assertNotNull(slist, "Entries in list");
        assertEquals(3, list.size(), "All entries in list");
        slist = c.findEntries("BOGUS", "ZUB");
        assertNotNull(slist, "Entries in default list");
        assertEquals(1, slist.size(), "Default value on entry list");

        // Test some default stuff
        assertEquals("BAR", c.findStringEntry("FOO", "FUZ"), "Get with default");
        assertEquals("BUZ", c.findStringEntry("ZUB", "BUZ"), "Get with default no val");

        // Now with an entry removed
        configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "FOO = \"SHAZAM\"\n" + "FOO != \"SHAZAM\"\n").getBytes();
        str = new ByteArrayInputStream(configData);
        c = ConfigUtil.getConfigInfo(str);
        assertEquals("BAR", c.findStringEntry("FOO"), "String entry finds first");
        list = c.findStringMatchEntries("FOO");
        assertNotNull(list, "Entries in string match list");
        assertEquals(2, list.size(), "All entries in string match list");
        set = c.findEntriesAsSet("FOO");
        assertNotNull(set, "Entries in string set");
        assertEquals(2, set.size(), "All entries in string match set");
        assertTrue(set.contains("BAR"), "Entry in set");
        assertTrue(set.contains("BAZ"), "Entry in set");
        assertFalse(set.contains("SHAZAM"), "Entry not in set");

        // Now with all entries removed
        configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "FOO = \"SHAZAM\"\n" + "FOO != \"*\"\n").getBytes();
        str = new ByteArrayInputStream(configData);
        c = ConfigUtil.getConfigInfo(str);
        assertNull(c.findStringEntry("FOO"), "No entry for string match");
        list = c.findStringMatchEntries("FOO");
        assertNotNull(list, "Entries in string match list");
        assertEquals(0, list.size(), "All entries in string match list");
        set = c.findEntriesAsSet("FOO");
        assertNotNull(set, "Entries in string set");
        assertEquals(0, set.size(), "All entries in string match set");
        assertFalse(set.contains("BAR"), "Entry not in set");
        assertFalse(set.contains("BAZ"), "Entry not in set");
        assertFalse(set.contains("SHAZAM"), "Entry not in set");

        // Test out the mapping methods
        configData = ("FOO_ONE = \"BAR\"\n" + "FOO_TWO = \"BAZ\"\n" + "FOO_THREE = \"SHAZAM\"\n" + "FOO_four = \"blaze\"\n").getBytes();
        str = new ByteArrayInputStream(configData);
        c = ConfigUtil.getConfigInfo(str);
        assertEquals("BAR", c.findStringEntry("FOO_ONE"), "Get entry");
        Map<String, String> map = c.findStringMatchMap("FOO_");
        assertNotNull(map, "Mapped entries");
        assertEquals(4, map.size(), "Mapped all entries");
        assertEquals("BAR", map.get("ONE"), "Mapped entry one");
        assertEquals("BAZ", map.get("TWO"), "Mapped entry two");
        assertEquals("SHAZAM", map.get("THREE"), "Mapped entry three");
        assertEquals("blaze", map.get("FOUR"), "Mapped entry four");
        assertFalse(map.containsKey("four"), "Mapped case not preserved");

        // preserve case this time
        map = c.findStringMatchMap("FOO_", true);
        assertNotNull(map, "Mapped entries");
        assertEquals(4, map.size(), "Mapped all entries");
        assertEquals("BAR", map.get("ONE"), "Mapped entry one");
        assertEquals("BAZ", map.get("TWO"), "Mapped entry two");
        assertEquals("SHAZAM", map.get("THREE"), "Mapped entry three");
        assertEquals("blaze", map.get("four"), "Mapped entry four");
        assertFalse(map.containsKey("FOUR"), "Mapped entry not upper cased");

        // preserve order this time
        map = c.findStringMatchMap("FOO_", true, true);
        List<String> keys = new ArrayList<>(map.keySet());
        List<String> values = new ArrayList<>(map.values());
        assertNotNull(map, "Mapped entries");
        assertEquals(4, map.size(), "Mapped all entries");
        assertEquals("ONE", keys.get(0), "Mapped key one");
        assertEquals("BAR", values.get(0), "Mapped value one");
        assertEquals("TWO", keys.get(1), "Mapped key two");
        assertEquals("BAZ", values.get(1), "Mapped value two");
        assertEquals("THREE", keys.get(2), "Mapped key three");
        assertEquals("SHAZAM", values.get(2), "Mapped value three");
        assertEquals("four", keys.get(3), "Mapped key four");
        assertEquals("blaze", values.get(3), "Mapped value four");

        // Try a nonexistent key
        map = c.findStringMatchMap("SHLOP_");
        assertNotNull(map, "Map created for non existent key");
        assertEquals(0, map.size(), "Empty map");
        map = c.findStringMatchMap(null);
        assertNotNull(map, "Map created for non existent key");
        assertEquals(0, map.size(), "Empty map");

    }

    @Test
    void testEntryPrimitives() throws IOException {
        final byte[] configData =
                ("INTEGER = \"123\"\n" + "LONG = \"123456\"\n" + "DOUBLE = \"12345678\"\n" + "BOOLEANT = \"TRUE\"\n" + "BOOLEANF = \"FALSE\"\n"
                        + "BOOLEANQ = \"BOGUS\"\n" + "SZP = \"123\"\n" + "SZB = \"123b\"\n" + "SZK = \"123k\"\n" + "SZM = \"123m\"\n"
                        + "SZG = \"123g\"\n" + "SZT = \"123t\"\n" + "SZQ = \"red balloons\"\n" + "SZ1 = \"111\"\n" + "SZ2 = \"222\"\n"
                        + "SZ3 = \"333\"\n" + "SZ4 = \"444\"\n" + "SZ5 = \"555\"\n" + "SZ6 = \"666\"\n" + "SZ7 = \"777\"\n" + "SZ8 = \"888\"\n"
                        + "SZ9 = \"999\"\n" + "SZ0 = \"000\"\n" + "STRING = \"chars\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c = ConfigUtil.getConfigInfo(str);
        assertEquals(123, c.findIntEntry("INTEGER", 456), "Int entry with def");
        assertEquals(456, c.findIntEntry("BLUBBER", 456), "Int entry no val");

        assertEquals(123456L, c.findLongEntry("LONG", 456L), "Long entry with def");
        assertEquals(456L, c.findLongEntry("BLUBBER", 456L), "Long entry no val");

        final double d = 12345678;
        final double d2 = 456;
        assertEquals(d, c.findDoubleEntry("DOUBLE", d2), 0d);
        assertEquals(d2, c.findDoubleEntry("BLUBBER", d2), 0d);

        assertTrue(c.findBooleanEntry("BOOLEANT", false), "Boolean entry");
        assertFalse(c.findBooleanEntry("BOOLEANF", true), "Boolean entry");
        assertTrue(c.findBooleanEntry("BLUBBER", true), "Boolean entry");
        assertFalse(c.findBooleanEntry("BLUBBER", false), "Boolean entry");
        assertTrue(c.findBooleanEntry("BLUBBER", "true"), "Boolean entry");
        assertFalse(c.findBooleanEntry("BLUBBER", "false"), "Boolean entry");
        assertTrue(c.findBooleanEntry("BOOLEANQ", true), "Boolean bad entry");
        assertFalse(c.findBooleanEntry("BOOLEANQ", false), "Boolean bad entry");

        assertEquals(666L, c.findSizeEntry("SZXYZ", 666L), "Size default tag");
        assertEquals(123L, c.findSizeEntry("SZP", -1L), "Size bytes no marker");
        assertEquals(123L, c.findSizeEntry("SZB", -1L), "Size bytes");
        assertEquals(123L * 1024, c.findSizeEntry("SZK", -1L), "Size kbytes");
        assertEquals(123L * 1024 * 1024, c.findSizeEntry("SZM", -1L), "Size mbytes");
        assertEquals(123L * 1024 * 1024 * 1024, c.findSizeEntry("SZG", -1L), "Size gbytes");
        assertEquals(123L * 1024 * 1024 * 1024 * 1024, c.findSizeEntry("SZT", -1L), "Size tbytes");
        assertEquals(123L, c.findSizeEntry("SZQ", 123L), "Size default");
        assertEquals(111L, c.findSizeEntry("SZ1", -1L), "Size digit check");
        assertEquals(222L, c.findSizeEntry("SZ2", -1L), "Size digit check");
        assertEquals(333L, c.findSizeEntry("SZ3", -1L), "Size digit check");
        assertEquals(444L, c.findSizeEntry("SZ4", -1L), "Size digit check");
        assertEquals(555L, c.findSizeEntry("SZ5", -1L), "Size digit check");
        assertEquals(666L, c.findSizeEntry("SZ6", -1L), "Size digit check");
        assertEquals(777L, c.findSizeEntry("SZ7", -1L), "Size digit check");
        assertEquals(888L, c.findSizeEntry("SZ8", -1L), "Size digit check");
        assertEquals(999L, c.findSizeEntry("SZ9", -1L), "Size digit check");
        assertEquals(0L, c.findSizeEntry("SZ0", -1L), "Size digit check");
    }

    @Test
    void testUTF8Chars() throws IOException {
        final String[] s = {
                "FOO = \"This is a wa\\\\ufb04e test \\\\xyz123 123\"\n", "FOO = \"This is a wa\\\\ufb04\"\n", "FOO = \"This is a \\\\u1D504\"\n",
                "FOO = \"This is a \\\\U1D504\"\n"};

        final StringBuilder extValue = new StringBuilder();
        extValue.append("This is a ").appendCodePoint(0x1D504);

        final String[] t = {"This is a wa\ufb04e test \\xyz123 123", "This is a wa\ufb04", extValue.toString(), extValue.toString()};

        for (int i = 0; i < s.length; i++) {
            final ServiceConfigGuide sc = new ServiceConfigGuide(new ByteArrayInputStream(s[i].getBytes()));
            assertEquals(t[i], sc.findStringEntry("FOO", null), "UTF8 entry conversion failed in slot " + i);
        }
    }

    @Test
    void testCreateDirAndFile() throws IOException {
        final Path tdir = Files.createTempDirectory(null);
        final Path tfile = Paths.get(tdir.toString(), "foo-file.txt");
        final Path t2file = Paths.get(tdir.toString(), "subdir", "blubdir", "bar-file.txt");
        String s =
                "CREATE_DIRECTORY = \"" + tdir + "\"\n" + "CREATE_FILE = \"" + tfile + "\"\n" + "CREATE_FILE = \""
                        + tfile + "\"\n" + "CREATE_FILE = \"" + t2file + "\"\n";
        s = s.replace('\\', '/');

        new ServiceConfigGuide(new ByteArrayInputStream(s.getBytes()));
        assertTrue(Files.exists(tdir), "Directory creation failed for " + tdir + " in " + s);
        assertTrue(Files.exists(tfile) && Files.isReadable(tfile), "File creation failed for " + tfile + " in " + s);
        assertTrue(Files.exists(t2file) && Files.isReadable(t2file), "File and directory creation failed for " + t2file + " in " + s);

        FileUtils.deleteDirectory(tdir.toFile());
    }

    @Test
    void testMagicSubstitutions() throws IOException {
        final String s =
                "TGT_HOST = \"MYHOST\"\n" + "TGT_DOMAIN = \"MYDOMAIN\"\n" + "TGT_PORT = \"9999\"\n" + "DEBUG = \"true\"\n" + "MYHOST = \"@{HOST}\"\n"
                        + "MYPROJ = \"@{PRJ_BASE}/bin\"\n" + "MYTMP = \"@{TMPDIR}\"\n" + "MYBOGUS = \"@{HEREITIS}\"\n"
                        + "MYURL = \"http://@{TGT_HOST}.@{TGT_DOMAIN}:@{TGT_PORT}/\"\n" + "MYLIB = \"thelib-@{OS.NAME}-@{OS.VER}-@{OS.ARCH}.so\"\n"
                        + "MYCFG = \"@{CONFIG_DIR}@{/}TheStuff.cfg\"\n";
        final ServiceConfigGuide sc = new ServiceConfigGuide(new ByteArrayInputStream(s.getBytes()));

        assertEquals("@{HEREITIS}", sc.findStringEntry("MYBOGUS"), "Replacement of bogus key is unexpected");

        assertEquals(-1, sc.findStringEntry("MYPROJ").indexOf("PRJ_BASE"), "Replacement of  PRJ_BASE with value failed");

        assertEquals(System.getProperty("java.io.tmpdir"), sc.findStringEntry("MYTMP"), "TMP_DIR magic replacement failed");

        assertFalse(sc.findStringEntry("MYHOST").contains("@"), "Replacement of magic HOST failed");

        assertEquals("http://MYHOST.MYDOMAIN:9999/", sc.findStringEntry("MYURL"), "MYURL constructed from replacements");

        assertEquals("thelib-" + System.getProperty("os.name").replace(' ', '_') + "-"
                + System.getProperty("os.version").replace(' ', '_') + "-" + System.getProperty("os.arch").replace(' ', '_') + ".so",
                sc.findStringEntry("MYLIB"),
                "MYLIB construction from os replacements failed");

        assertEquals(System.getProperty("emissary.config.dir", "/").replace('\\', '/')
                + "/TheStuff.cfg", sc.findStringEntry("MYCFG").replace('\\', '/'), "MYCFG dir construction from path replacements failed");

        assertTrue(sc.debug(), "Debug enable on config file failed");
    }

    @Test
    void testConstructors() throws IOException {
        this.cis.reset();
        final ServiceConfigGuide sc = new ServiceConfigGuide(this.cis);
        final String pn = sc.findStringEntry("INITIAL_FORM");
        assertNotNull(pn, "Value from stream");
        this.cis.reset();
        final ServiceConfigGuide sc2 = new ServiceConfigGuide(this.cis, "TestStream");
        assertEquals(pn, sc2.findStringEntry("INITIAL_FORM"), "Value from extraction");

        // Write the config bytes out to a temp file
        final File scfile = File.createTempFile("temp", ".cfg");
        scfile.deleteOnExit();
        try (final OutputStream os = Files.newOutputStream(scfile.toPath())) {
            os.write(cdata.getBytes());
        }

        final ServiceConfigGuide sc3 = new ServiceConfigGuide(scfile.getAbsolutePath());
        assertEquals(pn, sc3.findStringEntry("INITIAL_FORM"), "Read file from disk");
        final ServiceConfigGuide sc4 = new ServiceConfigGuide(scfile.getParent(), scfile.getName());
        assertEquals(pn, sc4.findStringEntry("INITIAL_FORM"), "Read dir and file from disk");
    }

    @Test
    void testMerge() throws IOException {
        final byte[] configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "KEY1 = \"VAL1\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c1 = ConfigUtil.getConfigInfo(str);
        final byte[] configData2 = ("FOO = \"BAR2\"\n" + "KEY2 = \"VAL2\"\n").getBytes();
        final ByteArrayInputStream str2 = new ByteArrayInputStream(configData2);
        final Configurator c2 = ConfigUtil.getConfigInfo(str2);

        c1.merge(c2);
        assertEquals("BAR2", c1.findStringEntry("FOO"), "Newly merged entry is first");
        assertEquals(3, c1.findEntries("FOO").size(), "Merge contains union of values");
        assertEquals("VAL1", c1.findStringEntry("KEY1"), "Old value present after merge");
        assertEquals("VAL2", c1.findStringEntry("KEY2"), "New value present after merge");
    }

    @Test
    void testMergeRemoval() throws IOException {
        final byte[] configData = ("FOO = \"BAR\"\n" + "FOO = \"BAZ\"\n" + "KEY1 = \"VAL1\"\n" + "KEY1 = \"VAL2\"\n").getBytes();
        final ByteArrayInputStream str = new ByteArrayInputStream(configData);
        final Configurator c1 = ConfigUtil.getConfigInfo(str);
        final byte[] configData2 = ("FOO != \"*\"\n" + "FOO = \"ZUB\"\n" + "KEY1 != \"VAL2\"\n").getBytes();
        final ByteArrayInputStream str2 = new ByteArrayInputStream(configData2);
        final Configurator c2 = ConfigUtil.getConfigInfo(str2);

        c1.merge(c2);

        assertEquals(1, c1.findEntries("FOO").size(), "Old values removed on *");
        assertEquals(1, c1.findEntries("KEY1").size(), "Specified value removed when listed");
        assertEquals("VAL1", c1.findStringEntry("KEY1"), "Non-specified value remains after remove");
    }

    @Test
    void testImportFileWhenFileExists() throws IOException {
        // Write the config bytes out to a temp file
        final Path dir = Files.createTempDirectory(null);
        final String priname = dir + "/primary.cfg";
        final String impname = dir + "/import.cfg";

        final byte[] primary = ("IMPORT_FILE = \"" + impname + "\"\n").getBytes();
        final byte[] importfile = ("FOO = \"BAR\"\n").getBytes();

        try {
            assertTrue(Executrix.writeDataToFile(primary, priname));
            assertTrue(Executrix.writeDataToFile(importfile, impname));
            new ServiceConfigGuide(priname);
        } catch (IOException iox) {
            // should not be reached due to IMPORT_FILE existing
            throw new AssertionError("IMPORT_FILE not found.", iox);
        } finally {
            FileUtils.deleteDirectory(dir.toFile());
        }
    }

    @Test
    void testImportFileWhenFileDoesNotExist() throws IOException {
        // Write the config bytes out to a temp file
        final Path dir = Files.createTempDirectory(null);
        final String priname = dir + "/primary.cfg";
        final String impname = dir + "/import.cfg";

        final byte[] primary = ("IMPORT_FILE = \"" + impname + "\"\n").getBytes();

        String result = "";
        String importFileName = Paths.get(impname).getFileName().toString();

        try {
            assertTrue(Executrix.writeDataToFile(primary, priname));
            new ServiceConfigGuide(priname);
        } catch (IOException iox) {
            // will catch as IMPORT_FILE is not created/found, String result will be thrown IO Exception Message
            result = iox.getMessage();
        } finally {
            FileUtils.deleteDirectory(dir.toFile());
        }

        String noImportExpectedMessage = "In " + priname + ", cannot find IMPORT_FILE: " + impname
                + " on the specified path. Make sure IMPORT_FILE (" + importFileName + ") exists, and the file path is correct.";

        assertEquals(result, noImportExpectedMessage, "IMPORT_FAIL Message Not What Was Expected.");
    }

    @Test
    void testOptImportWhenOptionalFileExists() throws IOException {
        // Write the config bytes out to a temp file
        final Path dir = Files.createTempDirectory(null);
        final String priname = dir + "/primary.cfg";
        final String optname = dir + "/optional.cfg";

        final byte[] primary = ("FOO = \"BAR\"\nOPT_IMPORT_FILE = \"" + optname + "\"\n").getBytes();
        final byte[] optional = ("FOO = \"BAR2\"\n").getBytes();

        final Configurator c;
        try {
            assertTrue(Executrix.writeDataToFile(primary, priname));
            assertTrue(Executrix.writeDataToFile(optional, optname));
            c = new ServiceConfigGuide(priname);
        } finally {
            FileUtils.deleteDirectory(dir.toFile());
        }

        assertEquals(2, c.findEntries("FOO").size(), "Optional value present");
        assertEquals("BAR2", c.findEntries("FOO").get(1), "Optional value present after primary");
    }

    @Test
    void testOptImportWhenOptionalFileDoesNotExist() throws IOException {
        // Write the config bytes out to a temp file
        final Path dir = Files.createTempDirectory(null);
        final String priname = dir + "/primary.cfg";
        final byte[] primary = ("FOO = \"BAR\"\nOPT_IMPORT_FILE = \"/tmp/bogus.cfg\"\n").getBytes();

        final Configurator c;
        try {
            assertTrue(Executrix.writeDataToFile(primary, priname));
            c = new ServiceConfigGuide(priname);
        } finally {
            FileUtils.deleteDirectory(dir.toFile());
        }
        assertEquals(1, c.findEntries("FOO").size(), "Optional value not present");
    }

    @Test
    void testOptImportWhenOptionalFileExistsButHasBadSyntax() throws IOException {
        // Write the config bytes out to a temp file
        final Path dir = Files.createTempDirectory(null);
        final String priname = dir + "/primary.cfg";
        final String optname = dir + "/optional.cfg";

        final byte[] primary = ("FOO = \"BAR\"\nOPT_IMPORT_FILE = \"" + optname + "\"\n").getBytes();
        final byte[] optional = ("FOO = \"BAR2\"\"\nBAD = \"LINE\"").getBytes();
        try {
            assertTrue(Executrix.writeDataToFile(primary, priname));
            assertTrue(Executrix.writeDataToFile(optional, optname));
            new ServiceConfigGuide(priname);
            fail("File parsing on OPT_IMPORT_FILE must fail when it has bad syntax");
        } catch (IOException iox) {
            // expected
        } finally {
            FileUtils.deleteDirectory(dir.toFile());
        }
    }

    @Test
    void testImportFromClasspath() throws IOException {
        final Configurator c = ConfigUtil.getConfigInfo("emissary.config.ServiceConfigGuideImportTest.cfg");
        assertEquals(2, c.findEntries("FOO").size(), "Values from original and import present");
    }

    /**
     * This test case validates the patch for Emissary #201: https://github.com/NationalSecurityAgency/emissary/issues/201.
     * <p>
     * It verifies that findStringEntry() returns the first non-null configured value or the provided default value if no
     * non-null values are configured for a property.
     */
    @Test
    void testFindStringEntry_WithDefaultAndConfiguredNull_Emissary201() {
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

        assertEquals(defaultValue, unconfiguredValue, "Unconfigured property should return default");
        assertEquals(defaultValue, nullValue, "Property set to null should return default");
        assertEquals(nonNullValue, multiValue, "Multi-valued property should return first non-null value");
        assertEquals(defaultValue, multiNullValue, "Multi-valued property with all nulls should return default");
    }
}
