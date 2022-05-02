package emissary.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkUnitTest {
    @Test
    void testConstructWorkUnit() {
        WorkUnit workUnit = new WorkUnit("file1");
        assertEquals("file1", workUnit.getFileName(), "WorkUnit filename should match");
        assertNull(workUnit.getTransactionId(), "Transaction ID should not be set");
        assertFalse(workUnit.failedToParse(), "WorkUnit failedToParse should be false");
        assertFalse(workUnit.failedToProcess(), "WorkUnit failedToParse should be false");
    }

    @Test
    void testSetTransactionId() {
        WorkUnit workUnit = new WorkUnit("file1");
        String txid = UUID.randomUUID().toString();
        workUnit.setTransactionId(txid);
        assertEquals(txid, workUnit.getTransactionId(), "Transaction ID did not match");
    }

    @Test
    void testSetFailedToParse() {
        WorkUnit workUnit = new WorkUnit("file1");
        workUnit.setFailedToParse();
        assertTrue(workUnit.failedToParse(), "WorkUnit failedToParse should be true");
    }

}
