package emissary.transaction;

import org.junit.Before;

import java.io.IOException;

public class TransactionManagerInMemoryCacheTest extends AbstractTransactionManagerTest {

    @Before
    public void setup() throws IOException {
        super.setup();
        manager = new TransactionManagerInMemoryCache();
        manager.configure();
        manager.add(tx);
    }

}
