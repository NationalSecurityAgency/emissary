package emissary.pickup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class WorkBundleTest extends UnitTest {

    private boolean compareWorkerUnits(WorkUnit w1, WorkUnit w2) {
        boolean matchFileNames;
        if (w1.getFileName() == null && w2.getFileName() == null) {
            matchFileNames = true;
        } else {
            matchFileNames = w1.getFileName().equals(w2.getFileName());
        }

        boolean matchTxid;
        if (w1.getTransactionId() == null && w2.getTransactionId() == null) {
            matchTxid = true;
        } else {
            matchTxid = w1.getTransactionId().equals(w2.getTransactionId());
        }

        return matchFileNames && matchTxid && (w1.failedToParse() == w2.failedToParse()) && (w1.failedToProcess() == w2.failedToProcess());
    }

    @Test
    public void testWorkBundleWithWorkerUnits() {
        WorkBundle w1 = new WorkBundle();
        w1.addWorkUnit(new WorkUnit("file1.txt"));
        w1.addWorkUnit(new WorkUnit("file2.txt", UUID.randomUUID().toString(), true, true));
        w1.addWorkUnit(new WorkUnit("file3.txt", UUID.randomUUID().toString(), true, false));
        assertEquals("Size of work units", 3, w1.getWorkUnitList().size());

        int ic = 0;
        for (Iterator<WorkUnit> i = w1.getWorkUnitIterator(); i.hasNext();) {
            i.next();
            ic++;
        }
        assertEquals("Files from iterator", 3, ic);

        WorkBundle w2 = new WorkBundle(w1);
        assertNotEquals("Copy ctor new id", w1.getBundleId(), w2.getBundleId());
        assertEquals("Failed to properly copy WorkUnit list", w1.getWorkUnitList(), w2.getWorkUnitList());

        String xml = w2.toXml();
        assertNotNull("XML failed to generate", xml);

        WorkBundle w3 = WorkBundle.buildWorkBundle(xml);
        assertNotNull("Failed to buildworkbundle from xml", w3);
        for (int i = 0; i < w2.getWorkUnitList().size(); i++) {
            if (!compareWorkerUnits(w2.getWorkUnitList().get(i), w3.getWorkUnitList().get(i))) {
                fail("BuildWorkBundle did not generate equivalent workBundle");
            }
        }
    }

    @Test
    public void testWorkBundle() {
        WorkBundle w1 = new WorkBundle();
        WorkBundle w2 = new WorkBundle();
        assertTrue("Generated ids must be unique " + w1.getBundleId(), !w1.getBundleId().equals(w2.getBundleId()));

        WorkBundle w3 = new WorkBundle("/output/root", "/eat/prefix");
        assertEquals("Output root stored", "/output/root", w3.getOutputRoot());
        assertEquals("Eat prefix stored", "/eat/prefix", w3.getEatPrefix());

        w3.addFileName("file1.txt");
        w3.addFileName("file2.txt");
        w3.addFileName("file3.txt");
        assertEquals("Size of file names", 3, w3.size());

        w3.addFileNames(new String[] {"file4.txt", "file5.txt", "file6.txt"});
        assertEquals("Size of file names", 6, w3.size());

        w3.addFileNames(Arrays.asList(new String[] {"file7.txt", "file8.txt"}));
        assertEquals("Size of file names", 8, w3.size());

        assertEquals("Files as list", 8, w3.getFileNameList().size());
        int ic = 0;
        for (Iterator<String> i = w3.getFileNameIterator(); i.hasNext();) {
            i.next();
            ic++;
        }
        assertEquals("Files from iterator", 8, ic);

        w3.setCaseId("caseid");
        w3.setSentTo("machine1");
        w3.setPriority(3);
        assertEquals("Error count return should be incremented", 1, w3.incrementErrorCount());

        WorkBundle w4 = new WorkBundle(w3);
        assertEquals("Copy constructor", "/output/root", w4.getOutputRoot());
        assertEquals("Copy constructor", "/eat/prefix", w4.getEatPrefix());
        assertFalse("Copy ctor new id", w3.getBundleId().equals(w4.getBundleId()));
        assertEquals("Copy constructor", "caseid", w4.getCaseId());
        assertEquals("Copy constructor", 8, w4.size());
        assertEquals("Copy of sentTo", "machine1", w4.getSentTo());
        assertEquals("Copy of errorCount", 1, w4.getErrorCount());
        assertEquals("Copy of priority", w3.getPriority(), w4.getPriority());
        assertEquals("Copy of simple mode flag", w3.getSimpleMode(), w4.getSimpleMode());

        String oldbid = w4.getBundleId();
        w4.resetBundleId();
        assertFalse("New id on reset", oldbid.equals(w4.getBundleId()));

        w3.clearFiles();
        assertEquals("Size after clear", 0, w3.size());

        WorkBundle w5 = new WorkBundle(w3);
        assertEquals("Size of copy of empty bundle", 0, w5.size());

        assertTrue("ToString with bundle id", w4.toString().indexOf(w4.getBundleId()) > -1);
    }

    @Test
    public void testBundleXml() {
        WorkBundle w = new WorkBundle("/output/root", "/eat/prefix");
        w.setCaseId("caseid");
        w.addFileName("file1.txt", 15L, 4L);
        w.addFileName("<file2.txt&foo=bar>", 7L, 10L);
        w.setPriority(1);
        w.setSimpleMode(false);

        String xml = null;
        try {
            xml = w.toXml();
        } catch (Exception ex) {
            fail("Cannot generate xml " + ex);
        }
        assertNotNull("Generated xml", xml);
        WorkBundle w2 = WorkBundle.buildWorkBundle(xml);
        assertNotNull("Generated from xml", w2);
        assertEquals("Value across xml", w.getOutputRoot(), w2.getOutputRoot());
        assertEquals("Value across xml", w.getEatPrefix(), w2.getEatPrefix());
        assertEquals("Value across xml", w.getCaseId(), w2.getCaseId());
        assertEquals("Youngest time across xml", w.getYoungestFileModificationTime(), w2.getYoungestFileModificationTime());
        assertEquals("Oldest time across xml", w.getOldestFileModificationTime(), w2.getOldestFileModificationTime());
        assertEquals("Total filesize across xml", w.getTotalFileSize(), w2.getTotalFileSize());
        assertEquals("Files across xml", w.size(), w2.size());
        assertEquals("Priority across xml", w.getPriority(), w2.getPriority());
        assertEquals("Simple mode across xml", w.getSimpleMode(), w2.getSimpleMode());
        List<String> w2l = w2.getFileNameList();
        assertNotNull("File list from xml", w2l);
        assertEquals("Size of file list from xml", 2, w2l.size());
        assertEquals("File values from xml", "file1.txt", w2l.get(0));
        assertEquals("File values from xml", "<file2.txt&foo=bar>", w2l.get(1));
    }

    @Test
    public void testBundleXmlWithDefaultTimes() {
        WorkBundle w = new WorkBundle("/output/root", "/eat/prefix");
        w.setCaseId("caseid");
        // Dont set any times here in the second arg (unlike testBundleXml())
        w.addFileName("file1.txt");
        w.addFileName("<file2.txt&foo=bar>");
        w.setPriority(1);
        w.setSimpleMode(false);

        String xml = null;
        try {
            xml = w.toXml();
        } catch (Exception ex) {
            fail("Cannot generate xml " + ex);
        }
        assertNotNull("Generated xml", xml);
        WorkBundle w2 = WorkBundle.buildWorkBundle(xml);
        assertNotNull("Generated from xml", w2);
        assertEquals("Value across xml", w.getOutputRoot(), w2.getOutputRoot());
        assertEquals("Value across xml", w.getEatPrefix(), w2.getEatPrefix());
        assertEquals("Value across xml", w.getCaseId(), w2.getCaseId());
        assertEquals("Youngest time across xml", Long.MIN_VALUE, w2.getYoungestFileModificationTime());
        assertEquals("Oldest time across xml", Long.MAX_VALUE, w2.getOldestFileModificationTime());
        assertEquals("Files across xml", w.size(), w2.size());
        assertEquals("Priority across xml", w.getPriority(), w2.getPriority());
        assertEquals("Simple mode across xml", w.getSimpleMode(), w2.getSimpleMode());
        List<String> w2l = w2.getFileNameList();
        assertNotNull("File list from xml", w2l);
        assertEquals("Size of file list from xml", 2, w2l.size());
        assertEquals("File values from xml", "file1.txt", w2l.get(0));
        assertEquals("File values from xml", "<file2.txt&foo=bar>", w2l.get(1));
    }

    @Test
    public void testBundleXmlWithNullFields() {
        WorkBundle w = new WorkBundle(null, null);
        w.setCaseId(null);
        w.addFileName("file1.txt");
        w.setPriority(1);
        w.setSimpleMode(false);

        String xml = null;
        try {
            xml = w.toXml();
        } catch (Exception ex) {
            fail("Cannot generate xml " + ex);
        }
        assertNotNull("Generated xml", xml);
        WorkBundle w2 = WorkBundle.buildWorkBundle(xml);
        assertNotNull("Generated from xml", w2);
        assertEquals("Value across xml", w.getOutputRoot(), w2.getOutputRoot());
        assertEquals("Value across xml", w.getEatPrefix(), w2.getEatPrefix());
        assertEquals("Value across xml", w.getCaseId(), w2.getCaseId());
    }

    @Test
    public void testComparator() {
        WorkBundle w1 = new WorkBundle("/output/root", "/eat/prefix");
        WorkBundle w2 = new WorkBundle("/output/root", "/eat/prefix");
        w1.setPriority(10);
        w2.setPriority(20);
        assertTrue("Comparator by priority should have lower number/higher priority first " + w1.compareTo(w2), w1.compareTo(w2) < 0);
        assertTrue("Comparator by priority should have lower number/higher priority first " + w2.compareTo(w1), w2.compareTo(w1) > 0);
    }

    @Test
    public void testAddFileNameWithTimes() {
        WorkBundle w = new WorkBundle("/output/root", "/eat/prefix");
        w.addFileName("file1.txt", 15L, 4L);
        w.addFileName("<file2.txt&foo=bar>", 7L, 10L);
        assertEquals(15L, w.getYoungestFileModificationTime());
        assertEquals(7L, w.getOldestFileModificationTime());
    }

}
