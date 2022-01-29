package emissary.config;

import static emissary.config.ConfigUtil.CONFIG_DIR_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import emissary.core.EmissaryException;
import emissary.test.core.UnitTest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigUtilTest extends UnitTest {

    private static final boolean isWindows = System.getProperty("os.name").contains("Window");

    private static List<Path> testFilesAndDirectories;

    private static String configDir;
    private Path CDIR;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        configDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY, ".");
        testFilesAndDirectories = new ArrayList<>();
        CDIR = Paths.get(configDir);
    }

    @AfterEach
    public void cleanupFlavorSettings() throws Exception {
        super.tearDown();
        for (Path f : testFilesAndDirectories) {
            if (Files.exists(f)) {
                if (Files.isDirectory(f)) {
                    FileUtils.deleteDirectory(f.toFile());
                } else {
                    Files.delete(f);
                }
            }
        }
        CDIR = null;
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
    }


    @Test
    void testPathGeneration() {
        String fakeBase = "/path/to/";
        if (isWindows) {
            fakeBase = "X:/path/";
        }
        assertEquals(fakeBase + "bar.cfg", ConfigUtil.getConfigFile(fakeBase + "bar.cfg"), "Path not needed");

        assertEquals(configDir + "/bar.cfg", ConfigUtil.getConfigFile("bar.cfg"), "Path local should add to file");

        assertEquals(fakeBase + "bar.cfg", ConfigUtil.getConfigFile("/foo", fakeBase + "bar.cfg"), "Path not needed");

        assertEquals(fakeBase + "bar.cfg", ConfigUtil.getConfigFile(fakeBase, "bar.cfg"), "Path local should add to file");

        // Now without trailing slash
        fakeBase = "/path/to";
        if (isWindows) {
            fakeBase = "X:/path";
        }
        assertEquals(fakeBase + "/bar.cfg", ConfigUtil.getConfigFile("/foo", fakeBase + "/bar.cfg"), "Path not needed");

        assertEquals(fakeBase + "/bar.cfg", ConfigUtil.getConfigFile(fakeBase, "bar.cfg"), "Path local should add to file");
    }

    private static final class Dummy {
        @SuppressWarnings("unused")
        public int getStuff() {
            return 1;
        }
    }

    @Test
    void testOldStyleNestedClassConfig() {
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(Dummy.class));
    }

    @Test
    void testBadPreferences() {
        final List<String> prefs = new ArrayList<>();
        prefs.add("foo");
        prefs.add("bar");
        prefs.add("quuz");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(prefs));
    }

    @Test
    void testEmptyFlavorNaming() throws EmissaryException {
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
        final String[] r = ConfigUtil.addFlavors("emissary.blubber.Whale.cfg");
        assertEquals(0, r.length, "Flavor cannot be added not " + Arrays.asList(r));
    }

    @Test
    void testSingleFlavorNaming() throws EmissaryException {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");
        emissary.config.ConfigUtil.initialize();

        assertEquals("emissary.blubber.Whale-TESTFLAVOR.cfg",
                ConfigUtil.addFlavors("emissary.blubber.Whale.cfg")[0],
                "Flavor should be added to resource name");
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testDoubleFlavorNaming() throws EmissaryException {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "CHOCOLATE,PEANUTBUTTER");
        emissary.config.ConfigUtil.initialize();

        final String[] fnames = ConfigUtil.addFlavors("emissary.blubber.Whale.cfg");
        assertEquals(2, fnames.length, "All flavors must be added");
        assertEquals("emissary.blubber.Whale-CHOCOLATE.cfg", fnames[0], "First flavor should be added to resource name");
        assertEquals("emissary.blubber.Whale-PEANUTBUTTER.cfg", fnames[1], "Second flavor should be added to resource name");

        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
    }


    @Test
    void testFlavorMerge() throws IOException, EmissaryException {
        // Set up a flavor for the test
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");
        emissary.config.ConfigUtil.initialize();

        final Path baseFile = Paths.get(configDir, "emissary.blubber.Whale.cfg");
        try (OutputStream ros = Files.newOutputStream(baseFile)) {
            ros.write("FOO = \"BAR\"\n".getBytes());
        }

        final Path flavFile = Paths.get(configDir, "emissary.blubber.Whale-TESTFLAVOR.cfg");
        try (OutputStream ros = Files.newOutputStream(flavFile)) {
            ros.write("FOO = \"BAR2\"\n".getBytes());
        }

        final Configurator c = ConfigUtil.getConfigInfo("emissary.blubber.Whale.cfg");
        assertNotNull(c, "Configuration should have been found");
        assertEquals(2, c.findEntries("FOO").size(), "Optional config value FOO should have been merged");
        assertEquals("BAR2", c.findStringEntry("FOO"), "Merged entry should be first");


        // Restore default flavor
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();

        // Clean up tmp files
        Files.deleteIfExists(baseFile);
        Files.deleteIfExists(flavFile);
    }

    @Test
    void testFlavorMergeWithVariableExpansion() throws IOException, EmissaryException {
        // Set up a flavor for the test
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");
        emissary.config.ConfigUtil.initialize();

        final Path baseFile = Paths.get(configDir, "emissary.blubber.Shark.cfg");
        try (OutputStream ros = Files.newOutputStream(baseFile)) {
            ros.write("FOO = \"BAR\"\n".getBytes());
        }

        final Path flavFile = Paths.get(configDir, "emissary.blubber.Shark-TESTFLAVOR.cfg");
        try (OutputStream ros = Files.newOutputStream(flavFile)) {
            ros.write("QUUZ = \"@{FOO}\"\n".getBytes());
        }

        final Configurator c = ConfigUtil.getConfigInfo("emissary.blubber.Shark.cfg");
        assertNotNull(c, "Configuration should have been found");
        assertEquals(1, c.findEntries("FOO").size(), "Optional config value FOO should have been merged");
        assertEquals(1, c.findEntries("QUUZ").size(), "Optional config value QUUZ should have been merged");
        assertEquals("BAR", c.findStringEntry("QUUZ"), "Merged entry should be expanded");


        // Restore default flavor
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();

        // Clean up tmp files
        Files.deleteIfExists(baseFile);
        Files.deleteIfExists(flavFile);
    }

    @Test
    void testPropertyInfo() {
        try {
            // A bogus prop object
            Properties p = ConfigUtil.getPropertyInfo("foo.properties");
            assertNotNull(p, "Empty properties returned");
            assertEquals(0, p.size(), "Empty properties returned");

            // A real prop object
            p = ConfigUtil.getPropertyInfo("emissary.config.fake.properties");
            assertNotNull(p, "Properties returned");
            assertTrue(p.size() > 0, "Non-empty properties returned");
        } catch (IOException iox) {
            fail("Should not throw on property info get: " + iox.getMessage());
        }
    }

    @Test
    void testMissingConfigInfo() {
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("emissary.i.am.gone.Missing-forever.cfg"));
    }

    @Test
    void testMultipleConfigDirs() throws IOException, EmissaryException {
        // setup
        final Path configDir1 = createTmpSubDir("config1");
        final String cfgName1 = "emissary.chunky.Monkey.cfg";
        createFileAndPopulate(configDir1, cfgName1, "FOO = \"BAR\"\n");
        final Path configDir2 = createTmpSubDir("config2");
        final String cfgName2 = "emissary.chunky.Panda.cfg";
        createFileAndPopulate(configDir2, cfgName2, "BUZZ = \"BAH\"\n");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1 + "," + configDir2);

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        final Configurator c1 = ConfigUtil.getConfigInfo(cfgName1);
        assertNotNull(c1, "Configuration should have been found");
        assertEquals("BAR", c1.findStringEntry("FOO"), "Entry FOO is there");
        final Configurator c2 = ConfigUtil.getConfigInfo(cfgName2);
        assertNotNull(c2, "Configuration should have been found");
        assertEquals("BAH", c2.findStringEntry("BUZZ"), "Entry BUZZ is there");

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testMissingMultipleConfigDirs() throws IOException, EmissaryException {
        // setup
        final Path configDir1 = createTmpSubDir("config1A");
        final String cfgName1 = "emissary.grapes.Monkey.cfg";
        createFileAndPopulate(configDir1, cfgName1, "BOO = \"HOO\"\n");
        final Path cfgName2 = Paths.get(CDIR.toString(), "configgone", "emissary.grapes.Panda.cfg");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1 + "," + cfgName2.getParent());

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        assertEquals("HOO", ConfigUtil.getConfigInfo(cfgName1).findStringEntry("BOO"), "Entry BOO is wrong");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(cfgName2.getFileName().toString()));

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testGetConfigDirWithMultipleConfigDirs() throws IOException, EmissaryException {
        // setup
        final String cfgName = "emissary.phish.Food.cfg";
        final Path configDir1 = createTmpSubDir("config1B");
        createFileAndPopulate(configDir1, cfgName, "BLACK = \"WHITE\"\n");
        final Path configDir2 = createTmpSubDir("config2B");
        createFileAndPopulate(configDir2, cfgName, "BLACK = \"RED\"\nGREEN = \"YELLOW\"\n");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1 + "," + configDir2);

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        final Configurator c = ConfigUtil.getConfigInfo(cfgName);
        assertNotNull(c, "Configuration should have been found");
        assertEquals("WHITE", c.findStringEntry("BLACK"), "Entry BLACK should be from config1B");
        assertNull(c.findStringEntry("GREEN"), "File from config2B should not have been merged");

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testMutlipleConfigDirsWithFlavors() throws IOException, EmissaryException {
        // Set up a flavor for the test
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");

        final Path configDir1 = createTmpSubDir("config1S");
        createFileAndPopulate(configDir1, "emissary.blubber.Shark.cfg", "FOO = \"BAR\"\n");
        final Path configDir2 = createTmpSubDir("config2B");
        createFileAndPopulate(configDir2, "emissary.blubber.Shark-TESTFLAVOR.cfg", "QUUZ = \"@{FOO}\"\nGREEN = \"YELLOW\"\n");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1 + "," + configDir2);

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        final Configurator c = ConfigUtil.getConfigInfo("emissary.blubber.Shark.cfg");
        assertNotNull(c, "Configuration should have been found");
        assertEquals(1, c.findEntries("FOO").size(), "Optional config value FOO should have been merged");
        assertEquals(1, c.findEntries("QUUZ").size(), "Optional config value QUUZ should have been merged");
        assertEquals("BAR", c.findStringEntry("QUUZ"), "Merged entry should be expanded");

        // Restore default flavor
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testGetConfigDirWithMultiple() throws EmissaryException, IOException {
        final Path configDir1 = createTmpSubDir("config1D");
        final Path configDir2 = createTmpSubDir("config2D");
        final Path configDir3 = createTmpSubDir("config3D");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1 + "," + configDir2 + "," + configDir3);

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        assertEquals(3, ConfigUtil.getConfigDirs().size(), "Should be 3 config dirs");

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testInitializeWithMultipleConfigDirs() throws EmissaryException, IOException {
        final Path configDir1 = createTmpSubDir("config1D");
        final Path configDir2 = createTmpSubDir("config2D");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1 + "," + configDir2);

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        assertEquals(2, ConfigUtil.getConfigDirs().size(), "Should be 2 config dirs");
        emissary.config.ConfigUtil.initialize();
        assertEquals(2, ConfigUtil.getConfigDirs().size(), "Should still be 2 config dirs");

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();

    }

    @Test
    void testGetFlavorFromFile() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(Paths.get(CDIR.toString() + "emissary.admin.MasterClassNames-flavor1.cfg").toFile());
        assertEquals("flavor1", flavor, "Flavors didn't match");
    }

    @Test
    void testGetMultipleFlavorFromFile() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(Paths.get(CDIR.toString(), "emissary.junk.TrunkPlace-f1,f2,f3.cfg").toFile());
        assertEquals("f1,f2,f3", flavor, "Flavors didn't match");
    }

    @Test
    void testGetFlavorsNotACfgFile() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(Paths.get(CDIR.toString(), "emissary.util.JunkPlace-f1.config").toFile());
        assertEquals("", flavor, "Should have been empty, not a cfg file");
    }

    @Test
    void testGetNoFlavor() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(Paths.get(CDIR.toString(), "emissary.util.PepperPlace.config").toFile());
        assertEquals("", flavor, "Should have been empty, no flavor");
    }

    @Test
    void testGetFlavorMultipleHyphens() {
        final String flavor =
                ConfigUtil.getFlavorsFromCfgFile(Paths.get(CDIR.toString(), "emissary.util.DrPibbPlace-flavor1-flavor2-flavor3.cfg").toFile());
        assertEquals("flavor3", flavor, "Should have been the last flavor");

    }

    private Path createTmpSubDir(final String name) throws IOException {
        final Path dir = Paths.get(CDIR.toString(), name);
        Files.createDirectory(dir);
        testFilesAndDirectories.add(dir);
        return dir;
    }

    private Path createFileAndPopulate(final Path dir, final String name, final String contents) {
        final Path file = Paths.get(dir.toString(), name);
        testFilesAndDirectories.add(file);
        try (OutputStream ros = Files.newOutputStream(file)) {
            ros.write(contents.getBytes());
        } catch (IOException ex) {
            logger.error("Problem making {}", file, ex);
            throw new RuntimeException(ex);
        }
        return file;
    }
}
