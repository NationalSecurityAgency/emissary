package emissary.config;

import static emissary.config.ConfigUtil.CONFIG_DIR_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import emissary.core.EmissaryException;
import emissary.test.core.LogbackCapture;
import emissary.test.core.UnitTest;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ConfigUtilTest extends UnitTest {

    private static boolean isWindows = System.getProperty("os.name").indexOf("Window") != -1;

    private static List<File> testFilesAndDirectories;

    private static String configDir;
    private File CDIR;

    @Override
    @Before
    public void setUp() throws Exception {
        configDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY, ".");
        testFilesAndDirectories = new ArrayList<>();
        CDIR = new File(configDir);
    }

    @After
    public void cleanupFlavorSettings() throws Exception {
        super.tearDown();
        for (File f : testFilesAndDirectories) {
            if (f.exists()) {
                logger.trace("Removing " + f.getAbsolutePath());
                try {
                    if (f.isDirectory()) {
                        FileUtils.deleteDirectory(f);
                    } else {
                        f.delete();
                    }
                } catch (IOException ex) {
                    logger.error("Problem deleting " + f.getAbsolutePath(), ex);
                }
            }
        }
        CDIR = null;
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
    }


    @Test
    public void testPathGeneration() {
        String fakeBase = "/path/to/";
        if (isWindows) {
            fakeBase = "X:/path/";
        }
        assertEquals("Path not needed", fakeBase + "bar.cfg", ConfigUtil.getConfigFile(fakeBase + "bar.cfg"));

        assertEquals("Path local should add to file", configDir + "/bar.cfg", ConfigUtil.getConfigFile("bar.cfg"));

        assertEquals("Path not needed", fakeBase + "bar.cfg", ConfigUtil.getConfigFile("/foo", fakeBase + "bar.cfg"));

        assertEquals("Path local should add to file", fakeBase + "bar.cfg", ConfigUtil.getConfigFile(fakeBase, "bar.cfg"));

        // Now without trailing slash
        fakeBase = "/path/to";
        if (isWindows) {
            fakeBase = "X:/path";
        }
        assertEquals("Path not needed", fakeBase + "/bar.cfg", ConfigUtil.getConfigFile("/foo", fakeBase + "/bar.cfg"));

        assertEquals("Path local should add to file", fakeBase + "/bar.cfg", ConfigUtil.getConfigFile(fakeBase, "bar.cfg"));
    }

    private static final class Dummy {
        @SuppressWarnings("unused")
        public int getStuff() {
            return 1;
        }
    }

    @Test
    public void testOldStyleNestedClassConfig() {
        try {
            ConfigUtil.getConfigInfo(Dummy.class);
            fail("This configuration should not exist");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testBadPreferences() {
        final List<String> prefs = new ArrayList<String>();
        prefs.add("foo");
        prefs.add("bar");
        prefs.add("quuz");
        try {
            ConfigUtil.getConfigInfo(prefs);
            fail("None of these prefs should exist");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testEmptyFlavorNaming() throws EmissaryException {
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
        final String[] r = ConfigUtil.addFlavors("emissary.blubber.Whale.cfg");
        assertEquals("Flavor cannot be added not " + Arrays.asList(r), 0, r.length);
    }

    @Test
    public void testSingleFlavorNaming() throws EmissaryException {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");
        emissary.config.ConfigUtil.initialize();

        assertEquals("Flavor should be added to resource name", "emissary.blubber.Whale-TESTFLAVOR.cfg",
                ConfigUtil.addFlavors("emissary.blubber.Whale.cfg")[0]);
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testDoubleFlavorNaming() throws EmissaryException {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "CHOCOLATE,PEANUTBUTTER");
        emissary.config.ConfigUtil.initialize();

        final String[] fnames = ConfigUtil.addFlavors("emissary.blubber.Whale.cfg");
        assertEquals("All flavors must be added", 2, fnames.length);
        assertEquals("First flavor should be added to resource name", "emissary.blubber.Whale-CHOCOLATE.cfg", fnames[0]);
        assertEquals("Second flavor should be added to resource name", "emissary.blubber.Whale-PEANUTBUTTER.cfg", fnames[1]);

        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
    }


    @Test
    public void testFlavorMerge() throws IOException, EmissaryException {
        // Set up a flavor for the test
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");
        emissary.config.ConfigUtil.initialize();

        final File baseFile = new File(configDir + "/emissary.blubber.Whale.cfg");
        FileOutputStream ros = new FileOutputStream(baseFile);
        ros.write("FOO = \"BAR\"\n".getBytes());
        ros.close();

        final File flavFile = new File(configDir + "/emissary.blubber.Whale-TESTFLAVOR.cfg");
        ros = new FileOutputStream(flavFile);
        ros.write("FOO = \"BAR2\"\n".getBytes());
        ros.close();

        final Configurator c = ConfigUtil.getConfigInfo("emissary.blubber.Whale.cfg");
        assertNotNull("Configuration should have been found", c);
        assertEquals("Optional config value FOO should have been merged", 2, c.findEntries("FOO").size());
        assertEquals("Merged entry should be first", "BAR2", c.findStringEntry("FOO"));


        // Restore default flavor
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();

        // Clean up tmp files
        if (baseFile.exists()) {
            baseFile.delete();
        }
        if (flavFile.exists()) {
            flavFile.delete();
        }
    }

    @Test
    public void testFlavorMergeWithVariableExpansion() throws IOException, EmissaryException {
        // Set up a flavor for the test
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");
        emissary.config.ConfigUtil.initialize();

        final File baseFile = new File(configDir + "/emissary.blubber.Shark.cfg");
        FileOutputStream ros = new FileOutputStream(baseFile);
        ros.write("FOO = \"BAR\"\n".getBytes());
        ros.close();

        final File flavFile = new File(configDir + "/emissary.blubber.Shark-TESTFLAVOR.cfg");
        ros = new FileOutputStream(flavFile);
        ros.write("QUUZ = \"@{FOO}\"\n".getBytes());
        ros.close();

        final Configurator c = ConfigUtil.getConfigInfo("emissary.blubber.Shark.cfg");
        assertNotNull("Configuration should have been found", c);
        assertEquals("Optional config value FOO should have been merged", 1, c.findEntries("FOO").size());
        assertEquals("Optional config value QUUZ should have been merged", 1, c.findEntries("QUUZ").size());
        assertEquals("Merged entry should be expanded", "BAR", c.findStringEntry("QUUZ"));


        // Restore default flavor
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();

        // Clean up tmp files
        if (baseFile.exists()) {
            baseFile.delete();
        }
        if (flavFile.exists()) {
            flavFile.delete();
        }
    }

    @Test
    public void testPropertyInfo() {
        try {
            // A bogus prop object
            Properties p = ConfigUtil.getPropertyInfo("foo.properties");
            assertNotNull("Empty properties returned", p);
            assertEquals("Empty properties returned", 0, p.size());

            // A real prop object
            p = ConfigUtil.getPropertyInfo("emissary.config.fake.properties");
            assertNotNull("Properties returned", p);
            assertTrue("Non-empty properties returned", p.size() > 0);
        } catch (IOException iox) {
            fail("Should not throw on property info get: " + iox.getMessage());
        }
    }

    @Test(expected = IOException.class)
    public void testMissingConfigInfo() throws IOException {
        ConfigUtil.getConfigInfo("emissary.i.am.gone.Missing-forever.cfg"); // throws IOException
    }

    @Test
    public void testMultipleConfigDirs() throws IOException, EmissaryException {
        // setup
        final File configDir1 = createTmpSubDir("config1");
        final String cfgName1 = "emissary.chunky.Monkey.cfg";
        createFileAndPopulate(configDir1, cfgName1, "FOO = \"BAR\"\n");
        final File configDir2 = createTmpSubDir("config2");
        final String cfgName2 = "emissary.chunky.Panda.cfg";
        createFileAndPopulate(configDir2, cfgName2, "BUZZ = \"BAH\"\n");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1.toString() + "," + configDir2.toString());

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        final Configurator c1 = ConfigUtil.getConfigInfo(cfgName1);
        assertNotNull("Configuration should have been found", c1);
        assertEquals("Entry FOO is there", "BAR", c1.findStringEntry("FOO"));
        final Configurator c2 = ConfigUtil.getConfigInfo(cfgName2);
        assertNotNull("Configuration should have been found", c2);
        assertEquals("Entry BUZZ is there", "BAH", c2.findStringEntry("BUZZ"));

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testMissingMultipleConfigDirs() throws IOException, EmissaryException {
        // setup
        final File configDir1 = createTmpSubDir("config1A");
        final String cfgName1 = "emissary.grapes.Monkey.cfg";
        createFileAndPopulate(configDir1, cfgName1, "BOO = \"HOO\"\n");
        final File cfgName2 = new File(CDIR + "/configgone/emissary.grapes.Panda.cfg");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1.toString() + "," + cfgName2.getParent());

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        assertEquals("Entry BOO is wrong", "HOO", ConfigUtil.getConfigInfo(cfgName1).findStringEntry("BOO"));
        try {
            // This doesn't exist
            ConfigUtil.getConfigInfo(cfgName2.getName());
            fail("Should have thrown IOException");
        } catch (IOException e) {
            // swallow, this is expected
        }

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testGetConfigDirWithMultipleConfigDirs() throws IOException, EmissaryException {
        // setup
        final String cfgName = "emissary.phish.Food.cfg";
        final File configDir1 = createTmpSubDir("config1B");
        createFileAndPopulate(configDir1, cfgName, "BLACK = \"WHITE\"\n");
        final File configDir2 = createTmpSubDir("config2B");
        createFileAndPopulate(configDir2, cfgName, "BLACK = \"RED\"\nGREEN = \"YELLOW\"\n");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1.toString() + "," + configDir2.toString());

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        final Configurator c = ConfigUtil.getConfigInfo(cfgName);
        assertNotNull("Configuration should have been found", c);
        assertEquals("Entry BLACK should be from config1B", "WHITE", c.findStringEntry("BLACK"));
        assertEquals("File from config2B should not have been merged", null, c.findStringEntry("GREEN"));

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testMutlipleConfigDirsWithFlavors() throws IOException, EmissaryException {
        // Set up a flavor for the test
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "TESTFLAVOR");

        final File configDir1 = createTmpSubDir("config1S");
        createFileAndPopulate(configDir1, "emissary.blubber.Shark.cfg", "FOO = \"BAR\"\n");
        final File configDir2 = createTmpSubDir("config2B");
        createFileAndPopulate(configDir2, "emissary.blubber.Shark-TESTFLAVOR.cfg", "QUUZ = \"@{FOO}\"\nGREEN = \"YELLOW\"\n");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1.toString() + "," + configDir2.toString());

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        final Configurator c = ConfigUtil.getConfigInfo("emissary.blubber.Shark.cfg");
        assertNotNull("Configuration should have been found", c);
        assertEquals("Optional config value FOO should have been merged", 1, c.findEntries("FOO").size());
        assertEquals("Optional config value QUUZ should have been merged", 1, c.findEntries("QUUZ").size());
        assertEquals("Merged entry should be expanded", "BAR", c.findStringEntry("QUUZ"));

        // Restore default flavor
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testGetConfigDirWithMultiple() throws EmissaryException {
        final File configDir1 = createTmpSubDir("config1D");
        final File configDir2 = createTmpSubDir("config2D");
        final File configDir3 = createTmpSubDir("config3D");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1.toString() + "," + configDir2.toString() + "," + configDir3.toString());

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        assertEquals("Should be 3 config dirs", 3, ConfigUtil.getConfigDirs().size());

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testInitializeWithMultipleConfigDirs() throws EmissaryException {
        final File configDir1 = createTmpSubDir("config1D");
        final File configDir2 = createTmpSubDir("config2D");
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, configDir1.toString() + "," + configDir2.toString());

        // run
        emissary.config.ConfigUtil.initialize();

        // assert
        assertEquals("Should be 2 config dirs", 2, ConfigUtil.getConfigDirs().size());
        emissary.config.ConfigUtil.initialize();
        assertEquals("Should still be 2 config dirs", 2, ConfigUtil.getConfigDirs().size());

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();

    }

    @Test
    public void testMasterClassNamesOneFile() throws IOException, EmissaryException {
        // read in current file
        // figure out number of entries

        emissary.config.ConfigUtil.initialize();

        final Configurator c = ConfigUtil.getMasterClassNames();
        assertNotNull("Configurator should not be null", c);
        // assertEquals("Should have 2 entries", 2, c.entryKeys().size());
        // assertEquals("Should have set DevNullPlace", "emissary.place.sample.DevNullPlace",
        // c.findStringEntry("DevNullPlace"));
    }

    @Test
    public void testMasterClassNamesMultipleFiles() throws IOException, EmissaryException {
        final String contents1 = "DevNullPlace         = \"emissary.place.sample.DevNullPlace\"\n";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-core.cfg", contents1);

        final String one = "Dev2NullPlace         = \"emissary.place.donotpickme.DevNullPlace\"\n";
        final String two = "DirectoryPlace       = \"emissary.directory.DirectoryPlace\"";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-modeone.cfg", one + two);

        final String three = "Dev3NullPlace         = \"emissary.place.iamtheone.DevNullPlace\"\n";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-modetwo.cfg", three);

        emissary.config.ConfigUtil.initialize();

        final Configurator c = ConfigUtil.getMasterClassNames();
        assertNotNull("Configurator should not be null", c);
        assertEquals("Should have 4 entries", 4, c.entryKeys().size());
        assertEquals("Should have set DevNullPlace", "emissary.place.sample.DevNullPlace", c.findStringEntry("DevNullPlace"));
        assertEquals("Should have set DirectoryPlace", "emissary.directory.DirectoryPlace", c.findStringEntry("DirectoryPlace"));
        assertEquals("Should have set Dev3NullPlace", "emissary.place.iamtheone.DevNullPlace", c.findStringEntry("Dev3NullPlace"));
    }

    @Ignore
    // causing issues TODO: fix this
    @Test
    public void testOldMasterClassNameNotRead() throws IOException, EmissaryException {
        // no longer reading MasterClassNames.cfg
        final String contents = "DevNullPlace         = \"emissary.place.sample.DevNullPlace\"\n";
        createFileAndPopulate(CDIR, "MasterClassNames.cfg", contents);

        emissary.config.ConfigUtil.initialize();

        Configurator c = null;
        try {
            c = ConfigUtil.getMasterClassNames();
        } catch (EmissaryException e) {
            // Swallow, this should happen
        }
        assertEquals("Configurator should be null", null, c);
    }

    @Test
    public void testOneMasterClassNamesMultipleDirs() throws IOException, EmissaryException {
        // setup
        final File cfgDir1 = createTmpSubDir("cfg1AB");
        final File cfgDir2 = createTmpSubDir("cfg2AB");
        final String one = "DevNullPlace         = \"emissary.place.donotpickme.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir1, "emissary.admin.MasterClassNames-cfgDir1.cfg", one);
        final String two = "BlahBlahPlace         = \"emissary.place.donotpickme.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-cfgDir2.cfg", two);
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.getAbsolutePath() + "," + cfgDir2.getAbsolutePath());

        // run
        ConfigUtil.initialize();
        final Configurator c = ConfigUtil.getMasterClassNames();

        // assert
        assertNotNull("Should have a configurator", c);
        assertEquals("Should be 2 keys", 2, c.entryKeys().size());
        assertEquals("DevNulPlace was not parsed", "emissary.place.donotpickme.DevNullPlace", c.findStringEntry("DevNullPlace"));
        assertEquals("BlahBlahPlace was not parsed", "emissary.place.donotpickme.DevNullPlace", c.findStringEntry("BlahBlahPlace"));

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testSameMasterClassNamesMultipleDirs() throws IOException, EmissaryException {
        // setup
        final File cfgDir1 = createTmpSubDir("cfg1ABC");
        final File cfgDir2 = createTmpSubDir("cfg2ABC");
        final String one = "DevNullPlace         = \"emissary.place.first.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir1, "emissary.admin.MasterClassNames-sames.cfg", one);
        final String two = "Dev2NullPlace         = \"emissary.place.second.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-sames.cfg", two);
        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.getAbsolutePath() + "," + cfgDir2.getAbsolutePath());
        emissary.config.ConfigUtil.initialize();

        // run
        final Configurator c = ConfigUtil.getMasterClassNames();

        // assert
        assertNotNull("Should have a configurator", c);
        assertEquals("Should be 2 key", 2, c.entryKeys().size());
        assertEquals("DevNullPlace was not parsed", "emissary.place.first.DevNullPlace", c.findStringEntry("DevNullPlace"));
        assertEquals("Dev2NullPlace was not parsed", "emissary.place.second.DevNullPlace", c.findStringEntry("Dev2NullPlace"));

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }


    @Test
    public void testMultipleMasterClassNamesMultipleDirs() throws IOException, EmissaryException {
        // setup
        final File cfgDir1 = createTmpSubDir("cfg1ABCD");
        final File cfgDir2 = createTmpSubDir("cfg2ABCD");
        final File cfgDir3 = createTmpSubDir("cfg3ABCD");
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
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.getAbsolutePath() + "," + cfgDir2.getAbsolutePath() + "," + cfgDir3.getAbsolutePath());
        emissary.config.ConfigUtil.initialize();

        // run
        final Configurator c = ConfigUtil.getMasterClassNames();

        // assert
        assertNotNull("Should have a configurator", c);
        assertEquals("Should be 5 key", 5, c.entryKeys().size());
        // replaces with the last one
        assertEquals("DevNullPlace was not parsed", "emissary.place.first.DevNullPlace", c.findStringEntry("DevNullPlace"));
        assertEquals("BleeBleeNullPlace was not parsed", "emissary.place.second.BleeNullPlace", c.findStringEntry("BleeBleeNullPlace"));
        assertEquals("BleeCheesePlace was not parsed", "emissary.place.second.BleeCheesePlace", c.findStringEntry("BleeCheesePlace"));

        // clean up
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testMasterClassNamesWarnsOnFlavor() throws IOException, EmissaryException {
        // final String contents = "DevNullPlace = \"emissary.place.sample.DevNullPlace\"\n";
        // createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames.cfg", contents);
        final String contents2 = "DevNullPlace         = \"emissary.place.second.DevNullPlace\"\n";
        createFileAndPopulate(CDIR, "emissary.admin.MasterClassNames-NORM.cfg", contents2);
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "NORM");
        emissary.config.ConfigUtil.initialize();

        LogbackCapture.start(emissary.config.ConfigUtil.class);
        emissary.config.ConfigUtil.initialize();
        ConfigUtil.getMasterClassNames();

        final String logOutput = LogbackCapture.stop();
        assertEquals("Should have logged about the flavor", true, logOutput.contains("appeared to be flavored with NORM"));

        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        emissary.config.ConfigUtil.initialize();
    }

    @Test
    public void testGetFlavorFromFile() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(new File(CDIR + "emissary.admin.MasterClassNames-flavor1.cfg"));
        assertEquals("Flavors didn't match", "flavor1", flavor);
    }

    @Test
    public void testGetMultipleFlavorFromFile() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(new File(CDIR + "emissary.junk.TrunkPlace-f1,f2,f3.cfg"));
        assertEquals("Flavors didn't match", "f1,f2,f3", flavor);
    }

    @Test
    public void testGetFlavorsNotACfgFile() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(new File(CDIR + "emissary.util.JunkPlace-f1.config"));
        assertEquals("Should have been empty, not a cfg file", "", flavor);
    }

    @Test
    public void testGetNoFlavor() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(new File(CDIR + "emissary.util.PepperPlace.config"));
        assertEquals("Should have been empty, no flavor", "", flavor);
    }

    @Test
    public void testGetFlavorMultipleHyphens() {
        final String flavor = ConfigUtil.getFlavorsFromCfgFile(new File(CDIR + "emissary.util.DrPibbPlace-flavor1-flavor2-flavor3.cfg"));
        assertEquals("Should have been the last flavor", "flavor3", flavor);

    }

    @Test
    public void testDuplicateEntryInMasterClassNamesThrowsIOException() throws IOException, EmissaryException {
        // setup
        final File cfgDir1 = createTmpSubDir("cfg1ABCDE");
        final File cfgDir2 = createTmpSubDir("cfg2ABCDE");
        final String one = "DevNullPlace         = \"emissary.place.first.DevNullPlace\"\n";
        createFileAndPopulate(cfgDir1, "emissary.admin.MasterClassNames.cfg", one);
        final String two = "BlahBlahPlace         = \"emissary.place.second.DevNullPlace\"\n";
        final String three = "DevNullPlace         = \"emissary.place.second.DevNullPlace2\"\n";
        createFileAndPopulate(cfgDir2, "emissary.admin.MasterClassNames-hasdups.cfg", two + three);

        final String origConfigDirProp = System.getProperty(CONFIG_DIR_PROPERTY);
        System.setProperty(CONFIG_DIR_PROPERTY, cfgDir1.getAbsolutePath() + "," + cfgDir2.getAbsolutePath());
        emissary.config.ConfigUtil.initialize();

        // run
        final Configurator c = ConfigUtil.getMasterClassNames();
        // clean up quick so other test don't fail
        System.setProperty(CONFIG_DIR_PROPERTY, origConfigDirProp);
        emissary.config.ConfigUtil.initialize();

        // assert
        assertNotNull("Should have a configurator", c);
        assertEquals("Should be 1 key", 1, c.entryKeys().size()); // second one is not loaded
        // replaces with the last one
        assertEquals("DevNullPlace was not parsed", "emissary.place.first.DevNullPlace", c.findStringEntry("DevNullPlace"));
        assertEquals("BlahBlahPlace should not have been", null, c.findStringEntry("BlahBlahPlace"));
        assertEquals("Should have a config error", true, ConfigUtil.hasConfigErrors());

    }

    private File createTmpSubDir(final String name) {

        final File dir = new File(CDIR + "/" + name);
        dir.mkdirs();
        testFilesAndDirectories.add(dir);
        return dir;
    }

    private File createFileAndPopulate(final File dir, final String name, final String contents) {
        final String filename = dir.getAbsolutePath() + "/" + name;
        final File file = new File(filename);
        testFilesAndDirectories.add(file);
        FileOutputStream ros = null;
        try {
            ros = new FileOutputStream(file);
            ros.write(contents.getBytes());
        } catch (FileNotFoundException ex) {
            logger.error("Problem making " + filename, ex);
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            logger.error("Problem making " + filename, ex);
            throw new RuntimeException(ex);
        } finally {
            if (ros != null) {
                try {
                    ros.close();
                } catch (IOException ex) {
                    logger.error("Problem closing " + ros.toString(), ex);
                    throw new RuntimeException(ex);
                }
            }
        }
        return file;
    }
}
