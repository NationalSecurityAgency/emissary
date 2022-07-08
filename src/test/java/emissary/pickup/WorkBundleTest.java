package emissary.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class WorkBundleTest extends UnitTest {

    private boolean compareWorkerUnits(WorkUnit w1, WorkUnit w2) {
        boolean matchFileNames;
        if (w1.getFileName() == null && w2.getFileName() == null) {
            matchFileNames = true;
        } else {
            assertNotNull(w1.getFileName());
            matchFileNames = w1.getFileName().equals(w2.getFileName());
        }

        boolean matchTxid;
        if (w1.getTransactionId() == null && w2.getTransactionId() == null) {
            matchTxid = true;
        } else {
            assertNotNull(w1.getTransactionId());
            matchTxid = w1.getTransactionId().equals(w2.getTransactionId());
        }

        return matchFileNames && matchTxid && (w1.failedToParse() == w2.failedToParse()) && (w1.failedToProcess() == w2.failedToProcess());
    }

    @Test
    void testWorkBundleWithWorkerUnits() {
        WorkBundle w1 = new WorkBundle();
        w1.addWorkUnit(new WorkUnit("file1.txt"));
        w1.addWorkUnit(new WorkUnit("file2.txt", UUID.randomUUID().toString(), true, true));
        w1.addWorkUnit(new WorkUnit("file3.txt", UUID.randomUUID().toString(), true, false));
        assertEquals(3, w1.getWorkUnitList().size(), "Size of work units");

        int ic = 0;
        for (Iterator<WorkUnit> i = w1.getWorkUnitIterator(); i.hasNext();) {
            i.next();
            ic++;
        }
        assertEquals(3, ic, "Files from iterator");

        WorkBundle w2 = new WorkBundle(w1);
        assertNotEquals(w1.getBundleId(), w2.getBundleId(), "Copy ctor new id");
        assertEquals(w1.getWorkUnitList(), w2.getWorkUnitList(), "Failed to properly copy WorkUnit list");

        String xml = w2.toXml();
        assertNotNull(xml, "XML failed to generate");

        WorkBundle w3 = WorkBundle.buildWorkBundle(xml);
        assertNotNull(w3, "Failed to buildworkbundle from xml");
        for (int i = 0; i < w2.getWorkUnitList().size(); i++) {
            if (!compareWorkerUnits(w2.getWorkUnitList().get(i), w3.getWorkUnitList().get(i))) {
                fail("BuildWorkBundle did not generate equivalent workBundle");
            }
        }
    }

    @Test
    void testWorkBundle() {
        WorkBundle w1 = new WorkBundle();
        WorkBundle w2 = new WorkBundle();
        assertNotEquals(w1.getBundleId(), w2.getBundleId(), "Generated ids must be unique " + w1.getBundleId());

        WorkBundle w3 = new WorkBundle("/output/root", "/eat/prefix");
        assertEquals("/output/root", w3.getOutputRoot(), "Output root stored");
        assertEquals("/eat/prefix", w3.getEatPrefix(), "Eat prefix stored");

        w3.addFileName("file1.txt");
        w3.addFileName("file2.txt");
        w3.addFileName("file3.txt");
        assertEquals(3, w3.size(), "Size of file names");

        w3.addFileNames(new String[] {"file4.txt", "file5.txt", "file6.txt"});
        assertEquals(6, w3.size(), "Size of file names");

        w3.addFileNames(Arrays.asList("file7.txt", "file8.txt"));
        assertEquals(8, w3.size(), "Size of file names");

        assertEquals(8, w3.getFileNameList().size(), "Files as list");
        int ic = 0;
        for (Iterator<String> i = w3.getFileNameIterator(); i.hasNext();) {
            i.next();
            ic++;
        }
        assertEquals(8, ic, "Files from iterator");

        w3.setCaseId("caseid");
        w3.setSentTo("machine1");
        w3.setPriority(3);
        assertEquals(1, w3.incrementErrorCount(), "Error count return should be incremented");

        WorkBundle w4 = new WorkBundle(w3);
        assertEquals("/output/root", w4.getOutputRoot(), "Copy constructor");
        assertEquals("/eat/prefix", w4.getEatPrefix(), "Copy constructor");
        assertNotEquals(w3.getBundleId(), w4.getBundleId(), "Copy ctor new id");
        assertEquals("caseid", w4.getCaseId(), "Copy constructor");
        assertEquals(8, w4.size(), "Copy constructor");
        assertEquals("machine1", w4.getSentTo(), "Copy of sentTo");
        assertEquals(1, w4.getErrorCount(), "Copy of errorCount");
        assertEquals(w3.getPriority(), w4.getPriority(), "Copy of priority");
        assertEquals(w3.getSimpleMode(), w4.getSimpleMode(), "Copy of simple mode flag");

        String oldbid = w4.getBundleId();
        w4.resetBundleId();
        assertNotEquals(oldbid, w4.getBundleId(), "New id on reset");

        w3.clearFiles();
        assertEquals(0, w3.size(), "Size after clear");

        WorkBundle w5 = new WorkBundle(w3);
        assertEquals(0, w5.size(), "Size of copy of empty bundle");

        assertTrue(w4.toString().contains(w4.getBundleId()), "ToString with bundle id");
    }

    @Test
    void testBundleXml() {
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
            fail("Cannot generate xml", ex);
        }
        assertNotNull(xml, "Generated xml");
        WorkBundle w2 = WorkBundle.buildWorkBundle(xml);
        assertNotNull(w2, "Generated from xml");
        assertEquals(w.getOutputRoot(), w2.getOutputRoot(), "Value across xml");
        assertEquals(w.getEatPrefix(), w2.getEatPrefix(), "Value across xml");
        assertEquals(w.getCaseId(), w2.getCaseId(), "Value across xml");
        assertEquals(w.getYoungestFileModificationTime(), w2.getYoungestFileModificationTime(), "Youngest time across xml");
        assertEquals(w.getOldestFileModificationTime(), w2.getOldestFileModificationTime(), "Oldest time across xml");
        assertEquals(w.getTotalFileSize(), w2.getTotalFileSize(), "Total filesize across xml");
        assertEquals(w.size(), w2.size(), "Files across xml");
        assertEquals(w.getPriority(), w2.getPriority(), "Priority across xml");
        assertEquals(w.getSimpleMode(), w2.getSimpleMode(), "Simple mode across xml");
        List<String> w2l = w2.getFileNameList();
        assertNotNull(w2l, "File list from xml");
        assertEquals(2, w2l.size(), "Size of file list from xml");
        assertEquals("file1.txt", w2l.get(0), "File values from xml");
        assertEquals("<file2.txt&foo=bar>", w2l.get(1), "File values from xml");
    }

    @Test
    void testBundleXmlWithDefaultTimes() {
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
            fail("Cannot generate xml", ex);
        }
        assertNotNull(xml, "Generated xml");
        WorkBundle w2 = WorkBundle.buildWorkBundle(xml);
        assertNotNull(w2, "Generated from xml");
        assertEquals(w.getOutputRoot(), w2.getOutputRoot(), "Value across xml");
        assertEquals(w.getEatPrefix(), w2.getEatPrefix(), "Value across xml");
        assertEquals(w.getCaseId(), w2.getCaseId(), "Value across xml");
        assertEquals(Long.MIN_VALUE, w2.getYoungestFileModificationTime(), "Youngest time across xml");
        assertEquals(Long.MAX_VALUE, w2.getOldestFileModificationTime(), "Oldest time across xml");
        assertEquals(w.size(), w2.size(), "Files across xml");
        assertEquals(w.getPriority(), w2.getPriority(), "Priority across xml");
        assertEquals(w.getSimpleMode(), w2.getSimpleMode(), "Simple mode across xml");
        List<String> w2l = w2.getFileNameList();
        assertNotNull(w2l, "File list from xml");
        assertEquals(2, w2l.size(), "Size of file list from xml");
        assertEquals("file1.txt", w2l.get(0), "File values from xml");
        assertEquals("<file2.txt&foo=bar>", w2l.get(1), "File values from xml");
    }

    @Test
    void testBundleXmlWithNullFields() {
        WorkBundle w = new WorkBundle(null, null);
        w.setCaseId(null);
        w.addFileName("file1.txt");
        w.setPriority(1);
        w.setSimpleMode(false);

        String xml = null;
        try {
            xml = w.toXml();
        } catch (Exception ex) {
            fail("Cannot generate xml", ex);
        }
        assertNotNull(xml, "Generated xml");
        WorkBundle w2 = WorkBundle.buildWorkBundle(xml);
        assertNotNull(w2, "Generated from xml");
        assertEquals(w.getOutputRoot(), w2.getOutputRoot(), "Value across xml");
        assertEquals(w.getEatPrefix(), w2.getEatPrefix(), "Value across xml");
        assertEquals(w.getCaseId(), w2.getCaseId(), "Value across xml");
    }

    @Test
    void testComparator() {
        WorkBundle w1 = new WorkBundle("/output/root", "/eat/prefix");
        WorkBundle w2 = new WorkBundle("/output/root", "/eat/prefix");
        w1.setPriority(10);
        w2.setPriority(20);
        assertTrue(w1.compareTo(w2) < 0, "Comparator by priority should have lower number/higher priority first " + w1.compareTo(w2));
        assertTrue(w2.compareTo(w1) > 0, "Comparator by priority should have lower number/higher priority first " + w2.compareTo(w1));
    }

    @Test
    void testAddFileNameWithTimes() {
        WorkBundle w = new WorkBundle("/output/root", "/eat/prefix");
        w.addFileName("file1.txt", 15L, 4L);
        w.addFileName("<file2.txt&foo=bar>", 7L, 10L);
        assertEquals(15L, w.getYoungestFileModificationTime());
        assertEquals(7L, w.getOldestFileModificationTime());
    }

    @Test
    void testSerDe() throws IOException {
        WorkBundle w1 = new WorkBundle("/output/root", "/etc/prefix");
        w1.addFileName("file1.txt", 15L, 4L);
        w1.addFileName("<file2.txt&foo=bar>", 7L, 10L);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bout);
        w1.writeToStream(out);
        out.close();
        bout.close();

        byte[] b = bout.toByteArray();

        ByteArrayInputStream bin = new ByteArrayInputStream(b);
        DataInputStream in = new DataInputStream(bin);
        WorkBundle w2 = WorkBundle.readFromStream(in);

        assertEquals(0, w1.compareTo(w2));
    }

    @Test
    void testLimitAdd() {
        // generate test data.
        final List<WorkUnit> wul = new ArrayList<>();
        for (int i = 0; i < WorkBundle.MAX_UNITS + 2; i++) {
            String fileName = UUID.randomUUID().toString();
            wul.add(new WorkUnit(fileName));
        }

        // test add list of WorkUnits
        WorkBundle wb = new WorkBundle();
        assertThrows(IllegalStateException.class, () -> wb.addWorkUnits(wul));

        // test add individual WorkUnits
        assertThrows(IllegalStateException.class, () -> wul.forEach(wb::addWorkUnit));
    }

    @Test
    void testLimitSerDe() throws IOException {
        // Craft an illegal stream.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bout);
        WorkBundle wb1 = new WorkBundle();
        // generate test data.
        final List<WorkUnit> wul = new ArrayList<>();
        for (int i = 0; i < WorkBundle.MAX_UNITS + 2; i++) {
            String fileName = UUID.randomUUID().toString();
            wul.add(new WorkUnit(fileName));
        }

        WorkBundle.writeUTFOrNull(wb1.bundleId, out);
        WorkBundle.writeUTFOrNull(wb1.outputRoot, out);
        WorkBundle.writeUTFOrNull(wb1.eatPrefix, out);
        WorkBundle.writeUTFOrNull(wb1.caseId, out);
        WorkBundle.writeUTFOrNull(wb1.sentTo, out);
        out.writeInt(wb1.errorCount);
        out.writeInt(wb1.priority);
        out.writeBoolean(wb1.simpleMode);
        out.writeLong(wb1.oldestFileModificationTime);
        out.writeLong(wb1.youngestFileModificationTime);
        out.writeLong(wb1.totalFileSize);
        out.writeInt(WorkBundle.MAX_UNITS + 2);
        for (WorkUnit u : wul) {
            u.writeToStream(out);
        }

        out.close();
        bout.close();

        assertThrows(IOException.class, () -> WorkBundle.readFromStream(new DataInputStream(new ByteArrayInputStream(bout.toByteArray()))));
    }
}
