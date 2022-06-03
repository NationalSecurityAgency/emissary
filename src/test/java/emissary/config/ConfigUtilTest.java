package emissary.config;

import emissary.core.EmissaryException;
import emissary.test.core.junit5.UnitTest;
import emissary.util.shell.Executrix;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static emissary.config.ConfigUtil.CONFIG_DIR_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ConfigUtilTest extends UnitTest {

    private static List<Path> testFilesAndDirectories;

    private ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger configLogger = (Logger) LoggerFactory.getLogger(emissary.config.ConfigUtil.class);

    private static String configDir;
    private Path CDIR;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        configDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY, ".");
        testFilesAndDirectories = new ArrayList<>();
        CDIR = Paths.get(configDir);

        appender = new ListAppender<>();
        appender.start();
        configLogger.addAppender(appender);
    }

    @AfterEach
    public void cleanupFlavorSettings() throws Exception {
        super.tearDown();
        configLogger.detachAppender(appender);
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

        assertEquals(fakeBase + "bar.cfg", ConfigUtil.getConfigFile(fakeBase + "bar.cfg"), "Path not needed");

        assertEquals(configDir + "/bar.cfg", ConfigUtil.getConfigFile("bar.cfg"), "Path local should add to file");

        assertEquals(fakeBase + "bar.cfg", ConfigUtil.getConfigFile("/foo", fakeBase + "bar.cfg"), "Path not needed");

        assertEquals(fakeBase + "bar.cfg", ConfigUtil.getConfigFile(fakeBase, "bar.cfg"), "Path local should add to file");

        // Now without trailing slash
        fakeBase = "/path/to";

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
            fail("Should not throw on property info get", iox);
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
    void testMasterClassNamesOneFile() throws IOException, EmissaryException {
        // read in current file
        // figure out number of entries

        emissary.config.ConfigUtil.initialize();

        final Configurator c = ConfigUtil.getMasterClassNames();
        assertNotNull(c, "Configurator should not be null");
    }

    @Test
    void testMasterClassNamesMultipleFiles() throws IOException, EmissaryException {
        final String contents1 = "DevNullPlace         = \"emissary.place.sample.DevNullPlace\"\n";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-core.cfg", contents1);

        final String one = "Dev2NullPlace         = \"emissary.place.donotpickme.DevNullPlace\"\n";
        final String two = "DirectoryPlace       = \"emissary.directory.DirectoryPlace\"";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-modeone.cfg", one + two);

        final String three = "Dev3NullPlace         = \"emissary.place.iamtheone.DevNullPlace\"\n";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-modetwo.cfg", three);

        emissary.config.ConfigUtil.initialize();

        final Configurator c = ConfigUtil.getMasterClassNames();
        assertNotNull(c, "Configurator should not be null");
        assertEquals(4, c.entryKeys().size(), "Should have 4 entries");
        assertEquals("emissary.place.sample.DevNullPlace", c.findStringEntry("DevNullPlace"), "Should have set DevNullPlace");
        assertEquals("emissary.directory.DirectoryPlace", c.findStringEntry("DirectoryPlace"), "Should have set DirectoryPlace");
        assertEquals("emissary.place.iamtheone.DevNullPlace", c.findStringEntry("Dev3NullPlace"), "Should have set Dev3NullPlace");
    }

    @Test
    void testNoMasterClassNamesFilesExist() throws EmissaryException, IOException {

        final Path noCfgsFolder = createTmpSubDir("folder_with_no_cfg_files");

        System.setProperty(CONFIG_DIR_PROPERTY, String.valueOf(noCfgsFolder.toAbsolutePath()));
        emissary.config.ConfigUtil.initialize();

        EmissaryException thrown = assertThrows(EmissaryException.class, () -> {
            final Configurator c = ConfigUtil.getMasterClassNames();
        });

        assertTrue(thrown.getMessage().contains("No places to start."));
    }

    @Test
    void testOldMasterClassNamesFileExistsButIsIgnored() throws EmissaryException, IOException {

        // create a config file using old/deprecated file name convention MasterClassNames.cfg
        final Path oldCfgsFolder = createTmpSubDir("folder_with_old_cfg_file_name");

        final String contents = "DevNullPlace         = \"emissary.place.sample.DevNullPlace\"\n";
        createFileAndPopulate(oldCfgsFolder, "MasterClassNames.cfg", contents);

        System.setProperty(CONFIG_DIR_PROPERTY, String.valueOf(oldCfgsFolder.toAbsolutePath()));
        emissary.config.ConfigUtil.initialize();

        EmissaryException thrown = assertThrows(EmissaryException.class, () -> {
            final Configurator c = ConfigUtil.getMasterClassNames();
        });

        assertTrue(thrown.getMessage().contains("No places to start."));
    }

    @Test
    void testOneMasterClassNamesMultipleDirs() throws IOException, EmissaryException {
        // setup
        final Path cfgDir1 = createTmpSubDir("cfg1AB");
        final Path cfgDir2 = createTmpSubDir("cfg2AB");
        final String one = "DevNullPlace         = \"emissary.place.donotpickme.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir1, "emissary.admin.MasterClassNames-cfgDir1.cfg", one);
        final String two = "BlahBlahPlace         = \"emissary.place.donotpickme.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-cfgDir2.cfg", two);
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.toAbsolutePath() + "," + cfgDir2.toAbsolutePath());

        // run
        ConfigUtil.initialize();
        final Configurator c = ConfigUtil.getMasterClassNames();

        // assert
        assertNotNull(c, "Should have a configurator");
        assertEquals(2, c.entryKeys().size(), "Should be 2 keys");
        assertEquals("emissary.place.donotpickme.DevNullPlace", c.findStringEntry("DevNullPlace"), "DevNulPlace was not parsed");
        assertEquals("emissary.place.donotpickme.DevNullPlace", c.findStringEntry("BlahBlahPlace"), "BlahBlahPlace was not parsed");

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testSameMasterClassNamesMultipleDirs() throws IOException, EmissaryException {
        // setup
        final Path cfgDir1 = createTmpSubDir("cfg1ABC");
        final Path cfgDir2 = createTmpSubDir("cfg2ABC");
        final String one = "DevNullPlace         = \"emissary.place.first.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir1, "emissary.admin.MasterClassNames-sames.cfg", one);
        final String two = "Dev2NullPlace         = \"emissary.place.second.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-sames.cfg", two);
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.toAbsolutePath() + "," + cfgDir2.toAbsolutePath());
        emissary.config.ConfigUtil.initialize();

        // run
        final Configurator c = ConfigUtil.getMasterClassNames();

        // assert
        assertNotNull(c, "Should have a configurator");
        assertEquals(2, c.entryKeys().size(), "Should be 2 key");
        assertEquals("emissary.place.first.DevNullPlace", c.findStringEntry("DevNullPlace"), "DevNullPlace was not parsed");
        assertEquals("emissary.place.second.DevNullPlace", c.findStringEntry("Dev2NullPlace"), "Dev2NullPlace was not parsed");

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }


    @Test
    void testMultipleMasterClassNamesMultipleDirs() throws IOException, EmissaryException {
        // setup
        final Path cfgDir1 = createTmpSubDir("cfg1ABCD");
        final Path cfgDir2 = createTmpSubDir("cfg2ABCD");
        final Path cfgDir3 = createTmpSubDir("cfg3ABCD");
        final String one = "DevNullPlace         = \"emissary.place.first.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir1, "emissary.admin.MasterClassNames-sames.cfg", one);
        final String two = "BlahBlahPlace         = \"emissary.place.second.DevNullPlace\"\n";
        final String three = "Dev2NullPlace         = \"emissary.place.second.DevNullPlace2\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-sames.cfg", two + three);
        final String four = "BleeBleeNullPlace         = \"emissary.place.second.BleeNullPlace\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-sames-two.cfg", four);
        final String five = "BleeCheesePlace         = \"emissary.place.second.BleeCheesePlace\"\n";
        createFileAndPopulate(cfgDir3, "emissary.admin.MasterClassNames-three.cfg", five);

        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.toAbsolutePath() + "," + cfgDir2.toAbsolutePath() + "," + cfgDir3.toAbsolutePath());
        emissary.config.ConfigUtil.initialize();

        // run
        final Configurator c = ConfigUtil.getMasterClassNames();

        // assert
        assertNotNull(c, "Should have a configurator");
        assertEquals(5, c.entryKeys().size(), "Should be 5 key");
        // replaces with the last one
        assertEquals("emissary.place.first.DevNullPlace", c.findStringEntry("DevNullPlace"), "DevNullPlace was not parsed");
        assertEquals("emissary.place.second.BleeNullPlace", c.findStringEntry("BleeBleeNullPlace"), "BleeBleeNullPlace was not parsed");
        assertEquals("emissary.place.second.BleeCheesePlace", c.findStringEntry("BleeCheesePlace"), "BleeCheesePlace was not parsed");

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    void testMasterClassNamesWarnsOnFlavor() throws IOException, EmissaryException {
        final String contents2 = "DevNullPlace         = \"emissary.place.second.DevNullPlace\"\n";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-NORM.cfg", contents2);
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "NORM");

        emissary.config.ConfigUtil.initialize();
        ConfigUtil.getMasterClassNames();

        // Confirm logs contain flavor message
        assertTrue(
                appender.list.stream()
                        .anyMatch(i -> i.getFormattedMessage()
                                .contains("appeared to be flavored with NORM")));

        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
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

    @Test
    void testDuplicateEntryInMasterClassNamesThrowsIOException() throws IOException, EmissaryException {
        // setup
        final Path cfgDir1 = createTmpSubDir("cfg1ABCDE");
        final Path cfgDir2 = createTmpSubDir("cfg2ABCDE");
        final String one = "DevNullPlace         = \"emissary.place.first.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir1, "emissary.admin.MasterClassNames.cfg", one);
        final String two = "BlahBlahPlace         = \"emissary.place.second.DevNullPlace\"\n";
        final String three = "DevNullPlace         = \"emissary.place.second.DevNullPlace2\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-hasdups.cfg", two + three);

        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.toAbsolutePath() + "," + cfgDir2.toAbsolutePath());
        emissary.config.ConfigUtil.initialize();

        // run
        final Configurator c = ConfigUtil.getMasterClassNames();
        // clean up quick so other test don't fail
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();

        // assert
        assertNotNull(c, "Should have a configurator");
        assertEquals(1, c.entryKeys().size(), "Should be 1 key"); // second one is not loaded
        // replaces with the last one
        assertEquals("emissary.place.first.DevNullPlace", c.findStringEntry("DevNullPlace"), "DevNullPlace was not parsed");
        assertNull(c.findStringEntry("BlahBlahPlace"), "BlahBlahPlace should not have been");
    }

    @Test
    void testMissingImportFileInConfig(@TempDir final Path dir) throws IOException {
        final String priname = dir + "/primary.cfg";
        final String impname = dir + "/import.cfg";
        final byte[] primary = ("IMPORT_FILE = \"" + impname + "\"").getBytes();

        // Initialize String[] for files for getConfigInfo(final String[] preferences)
        final String[] files = new String[] {priname};

        String result = "";
        String importFileName = Paths.get(impname).getFileName().toString();

        try {
            Executrix.writeDataToFile(primary, priname);
            ConfigUtil.getConfigInfo(files);
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
