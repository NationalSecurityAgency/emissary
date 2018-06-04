package emissary.transaction;

import org.junit.Test;

public class TransactionTest {

    @Test(expected = IllegalStateException.class)
    public void testStatusChangeFailToSuccess() {
        Transaction tx = new Transaction();
        tx.fail();
        tx.success();
    }

    @Test(expected = IllegalStateException.class)
    public void testStatusChangeSuccessToStart() {
        Transaction tx = new Transaction();
        tx.success();
        tx.start();
    }

}
