package emissary.pickup;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.UUID;

import org.junit.Test;

public class WorkUnitTest {
    @Test
    public void testConstructWorkUnit() {
        WorkUnit workUnit = new WorkUnit("file1");
        assertEquals("WorkUnit filename should match", "file1", workUnit.getFileName());
        assertNull("Transaction ID should not be set", workUnit.getTransactionId());
        assertFalse("WorkUnit failedToParse should be false", workUnit.failedToParse());
        assertFalse("WorkUnit failedToParse should be false", workUnit.failedToProcess());
    }

    @Test
    public void testSetTransactionId() {
        WorkUnit workUnit = new WorkUnit("file1");
        String txid = UUID.randomUUID().toString();
        workUnit.setTransactionId(txid);
        assertEquals("Transaction ID did not match", txid, workUnit.getTransactionId());
    }

    @Test
    public void testSetFailedToParse() {
        WorkUnit workUnit = new WorkUnit("file1");
        workUnit.setFailedToParse();
        assertTrue("WorkUnit failedToParse should be true", workUnit.failedToParse());
    }

}
