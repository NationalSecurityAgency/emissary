package emissary.transaction;

import com.google.common.collect.ImmutableMap;
import emissary.config.ConfigUtil;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.pickup.WorkBundle;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static emissary.transaction.TransactionService.INPUT_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransactionServiceTest extends UnitTest {

    private static final String ROOT_DIR = "target/data";
    private static final String OUTPUT_ROOT = ROOT_DIR + "/DoneData";
    private static final String EAT_PREFIX = ROOT_DIR + "/InputData";
    private static final String PENDING_DIR = ROOT_DIR + "/HoldData";
    private static final String FIXED_PATH = "feed/2018/01/01/testing.txt";
    private static final String INPUT_FILE = EAT_PREFIX + "/" + FIXED_PATH;
    private static final String PENDING_FILE = PENDING_DIR + "/" + FIXED_PATH;
    private static final String JSON_OUTPUT = "target/localoutput/json/testingoutput.json";
    private static final String XML_OUTPUT = "target/localoutput/xml/testingoutput.xml";
    private static final String PARENT_FILE_TYPE = "JSON";
    private static final String FILE_TYPE = "TEXT";

    private static final String FILETYPE = "FILETYPE";
    private static final String PARENT_FILETYPE = "PARENT_FILETYPE";
    private static final String CONTENT_URI_JSON = "CONTENT_URI_JSON";
    private static final String CONTENT_URI_XML = "CONTENT_URI_XML";
    private static final String TARGET_BIN = "TARGET_BIN";

    private TransactionService service;
    private TransactionManager manager;
    private WorkBundle bundle;
    private IBaseDataObject ibdo;
    private String emissaryNode;
    private Transaction ptx;
    private String ptxid;
    private Transaction tx;
    private String txid;

    @Before
    public void setup() throws Exception {
        manager = mock(TransactionManager.class);
        service = new TransactionService(ConfigUtil.getConfigInfo(TransactionServiceTest.class), manager);

        bundle = new WorkBundle(OUTPUT_ROOT, EAT_PREFIX);
        bundle.addFileName(INPUT_FILE);

        ibdo = DataObjectFactory.getInstance("payload".getBytes(StandardCharsets.UTF_8), INPUT_FILE, "???");
        ibdo.putParameter(TARGET_BIN, PENDING_FILE);
        ibdo.putParameter(INPUT_FILENAME, PENDING_FILE);
        ibdo.putParameter(FILETYPE, FILE_TYPE);
        ibdo.putParameter(PARENT_FILETYPE, PARENT_FILE_TYPE);

        ptxid = Transaction.generateTransactionId(bundle.getBundleId(), FIXED_PATH);
        ptx = new Transaction(ptxid);

        txid = ibdo.getInternalId().toString();
        tx = new Transaction(txid);

        emissaryNode = System.getProperty("emissary.node.name") + "_" + System.getProperty("emissary.node.port");
    }

    @Test
    public void createWB() throws Exception {
        final ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        service.create(bundle);
        verify(manager, times(1)).log(captor.capture());
        verify(manager, times(0)).add(any(Transaction.class));
        assertTransaction(captor.getValue(), ptxid, Transaction.Status.CREATED);
        assertTransactionMetadata(captor.getValue());
    }

    @Test
    public void startWB() throws Exception {
        final ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        service.start(bundle);
        verify(manager, times(1)).log(any(Transaction.class));
        verify(manager, times(1)).add(captor.capture());
        assertTransaction(captor.getValue(), ptxid, Transaction.Status.STARTED);
        assertTransactionMetadata(captor.getValue());
    }

    @Test
    public void commitWB() throws Exception {
        when(manager.get(anyString())).thenReturn(ptx);
        service.commit(bundle);
        verify(manager, times(1)).update(ptx);
        verify(manager, times(1)).log(ptx);
        assertTransaction(ptx, ptxid, Transaction.Status.SUCCESS);
    }

    @Test
    public void rollbackWB() throws Exception {
        when(manager.get(anyString())).thenReturn(ptx);
        service.fail(bundle);
        verify(manager, times(1)).update(ptx);
        verify(manager, times(1)).log(ptx);
        verify(manager, times(1)).remove(ptx);
        assertTransaction(ptx, ptxid, Transaction.Status.FAILED);
    }

    @Test
    public void startIBDO() throws Exception {
        when(manager.get(anyString())).thenReturn(ptx);
        assertFalse(ptx.hasTransactions());

        service.start(ibdo);

        verify(manager, times(1)).update(ptx);
        assertTrue(ptx.hasTransactions());

        assertTransaction(ptx.getTransaction(txid), txid, Transaction.Status.STARTED);
        assertTransactionMetadata(ptx.getTransaction(txid));
    }

    @Test
    public void updateIBDO() throws Exception {
        ptx.addTransaction(tx);
        when(manager.get(anyString())).thenReturn(ptx);

        service.update(Collections.singletonList(ibdo), ImmutableMap.of(CONTENT_URI_JSON, JSON_OUTPUT, CONTENT_URI_XML, XML_OUTPUT));

        verify(manager, times(1)).update(ptx);

        assertTransaction(ptx.getTransaction(txid), txid, Transaction.Status.CREATED);
        assertEquals("Json content uri not matching", JSON_OUTPUT, tx.getMetadata(CONTENT_URI_JSON).toArray()[0]);
        assertEquals("Xml content uri not matching", XML_OUTPUT, tx.getMetadata(CONTENT_URI_XML).toArray()[0]);
    }

    @Test
    public void commitIBDO() throws Exception {
        ptx.success();
        ptx.addTransaction(tx);
        when(manager.get(anyString())).thenReturn(ptx);

        service.commit(ibdo);

        verify(manager, times(1)).update(ptx);
        verify(manager, times(1)).remove(ptx);

        assertTransaction(ptx.getTransaction(txid), txid, Transaction.Status.SUCCESS);
        assertEquals("Json content uri not matching", FILE_TYPE, tx.getMetadata(FILETYPE).toArray()[0]);
        assertEquals("Xml content uri not matching", PARENT_FILE_TYPE, tx.getMetadata(PARENT_FILETYPE).toArray()[0]);
    }

    @Test
    public void rollbackIBDO() throws Exception {
        ptx.fail();
        ptx.addTransaction(tx);
        when(manager.get(anyString())).thenReturn(ptx);

        service.fail(ibdo);

        verify(manager, times(1)).update(ptx);
        verify(manager, times(1)).remove(ptx);

        assertTransaction(ptx.getTransaction(txid), txid, Transaction.Status.FAILED);
        assertEquals("Json content uri not matching", FILE_TYPE, tx.getMetadata(FILETYPE).toArray()[0]);
        assertEquals("Xml content uri not matching", PARENT_FILE_TYPE, tx.getMetadata(PARENT_FILETYPE).toArray()[0]);
    }

    @Test
    public void startup() throws Exception {
        ptx.addTransaction(tx);
        when(manager.getAll()).thenReturn(Collections.singletonList(ptx));

        service.startup();

        assertTransaction(ptx, ptxid, Transaction.Status.FAILED);
    }

    @Test
    public void shutdown() throws Exception {
        ptx.addTransaction(tx);
        when(manager.getAll()).thenReturn(Collections.singletonList(ptx));

        service.shutdown();

        assertTransaction(ptx, ptxid, Transaction.Status.FAILED);
    }

    private void assertTransaction(Transaction t, String id, Transaction.Status status) {
        assertNotNull("Transaction should not be null", t);
        assertEquals("Ids not matching", id, t.getId());
        assertEquals("Status not matching", status, t.getStatus());
    }

    private void assertTransactionMetadata(Transaction t) {
        assertEquals("Node not matching", emissaryNode, t.getMetadata(TransactionService.META_PROCESSING_NODE).toArray()[0]);
        assertEquals("File name not matching", FIXED_PATH, t.getMetadata(TransactionService.META_FILE_NAME).toArray()[0]);
    }
}
