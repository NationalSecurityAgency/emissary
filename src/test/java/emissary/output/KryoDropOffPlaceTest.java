package emissary.output;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.kryo.BDODeserialzerIterator;
import emissary.output.filter.IDropOffFilter;
import emissary.roll.Roller;
import emissary.test.core.UnitTest;
import emissary.util.UnitTestSecurityManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KryoDropOffPlaceTest extends UnitTest {


    private final static String TEST_OUTPUT = "kryo-test";
    private final static Integer TEST_APPENDER_COUNT = 2;
    private final static Integer TEST_ROLLER_COUNT = 3;
    private Path outputDir;

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        // TODO: Need to delete the kryo-test directory at tear down time. Not working currently
        outputDir = Files.createTempDirectory(TEST_OUTPUT);
        System.setSecurityManager(new UnitTestSecurityManager());
    }

    @After
    @Override
    public void tearDown() throws Exception
    {
        System.setSecurityManager(null);
        super.tearDown();
    }

    public KryoDropOffPlace setupPlace() throws IOException {
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry(IDropOffFilter.OUTPUT_PATH, outputDir.toString());
        cfg.addEntry(KryoDropOffPlace.MAX_OUTPUT_APPENDERS, Integer.toString(TEST_APPENDER_COUNT));
        cfg.addEntry(Roller.CFG_ROLL_INTERVAL, Integer.toString(TEST_ROLLER_COUNT));
        return new KryoDropOffPlace(cfg);
    }

    @Test
    public void testConfiguration() throws Exception {
        KryoDropOffPlace kryoDropOffPlace = setupPlace();

        assertTrue(TEST_APPENDER_COUNT == kryoDropOffPlace.maxOutputAppenders);
        assertTrue(outputDir.toString().equals(kryoDropOffPlace.outputPath.toString()));
        assertTrue(TEST_ROLLER_COUNT == kryoDropOffPlace.interval);
    }

    @Test
    public void testBadConfig() throws Exception {
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry(IDropOffFilter.OUTPUT_PATH, "/BAD_PATH");
        try {
            new KryoDropOffPlace(cfg);
        } catch (SecurityException se) {
            assertTrue(se.getMessage().equals(UnitTestSecurityManager.SYSTEM_EXIT));
            return;
        }
        fail("Security exception should be thrown for the System.exit");
    }

    @Test
    public void testJournaledStartupError() throws Exception {
        final Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry(IDropOffFilter.OUTPUT_PATH, TEST_OUTPUT);
        cfg.addEntry(KryoDropOffPlace.MAX_OUTPUT_APPENDERS, Integer.toString(TEST_APPENDER_COUNT));
        cfg.addEntry(Roller.CFG_ROLL_INTERVAL, Integer.toString(TEST_ROLLER_COUNT));
        KryoDropOffPlace testPlace = Mockito.spy(new KryoDropOffPlace(cfg));

        Mockito.doThrow(new IOException()).when(testPlace).getCoalescer(Paths.get(TEST_OUTPUT), TEST_APPENDER_COUNT);

        try {
            testPlace.configurePlace();
        } catch (SecurityException se) {
            assertTrue(se.getMessage().equals(UnitTestSecurityManager.SYSTEM_EXIT));
            return;
        }
        fail("Security exception should be thrown for the System.exit");
    }

    @Test
    public void testIbdo() throws Exception {

        KryoDropOffPlace kryoDropOffPlace = setupPlace();
        final IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.setParameter("test-field", "test-value");
        final List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);
        kryoDropOffPlace.agentProcessHeavyDuty(payloadList);

        int partCount = 0;
        List<String> fileNames = new ArrayList<>();
        for (Path outputFile : Files.newDirectoryStream(outputDir, "*.bgpart")) {
            partCount++;
            fileNames.add(outputFile.getFileName().toString());
            BDODeserialzerIterator bdo = new BDODeserialzerIterator(outputFile);
            assertTrue(bdo.hasNext());
            List<IBaseDataObject> ibdoList = bdo.next();
            assertTrue(ibdoList.size() == 1);
            assertTrue(ibdoList.get(0).getFilename().equals("/this/is/a/testfile"));
            assertTrue(ibdoList.get(0).getFileType().equals("FTYPE"));
            assertTrue(new String(ibdoList.get(0).data()).equals("This is the data"));
            assertTrue(ibdoList.get(0).getParameter("test-field").get(0).equals("test-value"));
        }
        assertTrue(partCount == 1);

        int journalCount = 0;
        fileNames = new ArrayList<>();
        for (Path outputFile : Files.newDirectoryStream(outputDir, "*.bgpart.bgjournal")) {
            journalCount++;
            fileNames.add(outputFile.getFileName().toString());
        }
        assertTrue(journalCount == 1);
    }


}
