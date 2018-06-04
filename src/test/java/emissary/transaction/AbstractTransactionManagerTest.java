package emissary.transaction;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import static emissary.transaction.Transaction.Status.CREATED;
import static emissary.transaction.Transaction.Status.STARTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class AbstractTransactionManagerTest extends UnitTest {

    TransactionManager manager;
    Transaction tx;
    String id;

    @Before
    public void setup() throws IOException {
        id = UUID.randomUUID().toString();
        tx = new Transaction(id);
    }

    @After
    public void teardown() {
        try {
            manager.stop();
        } catch (Exception e) {

        }
    }

    @Test
    public void getAll() throws Exception {
        Collection<Transaction> actual = manager.getAll();
        assertNotNull(actual);
        assertEquals(1, actual.size());
        actual.forEach(t -> assertEquals(id, t.getId()));
    }

    @Test
    public void getById() throws Exception {
        Transaction actual = manager.get(id);
        assertNotNull(actual);
        assertEquals(id, actual.getId());
        assertEquals(CREATED, actual.getStatus());
    }

    @Test
    public void update() throws Exception {
        tx.start();
        manager.update(tx);

        Transaction actual = manager.get(id);
        assertNotNull(actual);
        assertEquals(id, actual.getId());
        assertEquals(STARTED, actual.getStatus());
    }

    @Test
    public void removeById() throws Exception {
        manager.remove(tx);

        Transaction actual = manager.get(id);
        assertNull(actual);
    }

    @Test
    public void stop() throws Exception {
        manager.stop();

        Transaction actual = manager.get(id);
        assertNull(actual);

        Collection<Transaction> list = manager.getAll();
        assertTrue(list.isEmpty());
    }

    @Test
    public void log() throws Exception {
        manager.add(new Transaction("1"));
        Transaction tx2 = new Transaction("2");
        tx2.addTransaction(new Transaction("3"));
        manager.add(tx2);
        manager.log();
    }

    @Test
    public void log2() throws Exception {
        manager.log(tx);
    }

    /* Tests for exceptions */

    @Test
    public void getNotFound() throws Exception {
        assertNull(manager.get("12345"));
    }

    @Test(expected = IOException.class)
    public void getBlank() throws Exception {
        manager.get("  ");
    }

    @Test(expected = IOException.class)
    public void getNull() throws Exception {
        manager.get(null);
    }

    @Test(expected = IOException.class)
    public void addExisting() throws Exception {
        manager.add(tx);
    }

    @Test(expected = IOException.class)
    public void addNull() throws Exception {
        manager.add(null);
    }

    @Test(expected = IOException.class)
    public void updateDne() throws Exception {
        manager.update(new Transaction("1234"));
    }

    @Test(expected = IOException.class)
    public void updateNull() throws Exception {
        manager.update(null);
    }

    @Test(expected = IOException.class)
    public void removeNull() throws Exception {
        manager.remove((Transaction) null);
    }

}
