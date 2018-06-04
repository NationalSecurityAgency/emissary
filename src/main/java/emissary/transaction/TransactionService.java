package emissary.transaction;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.core.IBaseDataObject;
import emissary.pickup.WorkBundle;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static emissary.transaction.Transaction.Status.CREATED;
import static emissary.transaction.Transaction.Status.FAILED;
import static emissary.transaction.Transaction.Status.STARTED;
import static emissary.transaction.Transaction.Status.SUCCESS;
import static emissary.transaction.Transaction.Status.TIMEOUT;

public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String META_BUNDLE_ID = "bundle_id";
    public static final String META_FILE_NAME = "file_name";
    public static final String META_PRIORITY = "priority";
    public static final String META_PROCESSING_NODE = "processing_node";
    public static final String META_PARENT_TX_ID = "parent_tx_id";
    public static final String META_SHORTNAME = "shortname";
    public static final String META_FILE_DATE = "file_date";

    public static final String INPUT_FILENAME = "INPUT_FILENAME";
    public static final String CONTENT_URI = "CONTENT_URI_";
    public static final String FILE_DATE = "FILE_DATE";

    protected Configurator conf;
    protected boolean active;
    protected boolean shutdown;
    protected TransactionManager manager;
    protected String inputFolder;
    protected String pendingFolder;
    protected String emissaryNode;
    protected List<String> outputParams;

    public TransactionService() throws IOException {
        configure(ConfigUtil.getConfigInfo(TransactionService.class));
    }

    public TransactionService(Configurator config) throws IOException {
        configure(config);
    }

    public TransactionService(Configurator config, TransactionManager manager) throws IOException {
        this.manager = manager;
        configure(config);
    }

    @SuppressWarnings("unchecked")
    protected void configure(Configurator config) throws IOException {
        conf = config;

        // test if active
        active = conf.findBooleanEntry("ACTIVE", false);
        if (!active) {
            shutdown = true;
            return;
        }

        inputFolder = conf.findStringEntry("INPUT_DATA");
        pendingFolder = conf.findStringEntry("HOLDING_AREA");

        emissaryNode = conf.findStringEntry("EMISSARY_NODE");
        outputParams = conf.findEntries("ADD_PARAMS");

        // setup the transaction manager
        if (manager == null) {
            String txmgrClass = conf.findStringEntry("TXMGR_CLASS", TransactionManagerInMemoryCache.class.getName());
            manager = (TransactionManager) Factory.create(txmgrClass);
            manager.configure();
        }
    }

    /**
     * Create a transaction based on the work bundle
     *
     * This is generally run on the feeder for tracking purposes.
     *
     * @param workBundle the work bundle containing the file(s) to process
     * @throws IOException if there is an issue
     */
    public void create(WorkBundle workBundle) {
        if (!valid(workBundle))
            return;
        logger.debug("Creating transaction for workBundle[{}]", workBundle.getBundleId());
        process(workBundle, CREATED);
    }

    /**
     * Start/Create a transaction on the work bundle
     *
     * @param workBundle the work bundle containing the file(s) to process
     * @throws IOException if there is an issue
     */
    public void start(WorkBundle workBundle) {
        if (!valid(workBundle))
            return;
        logger.debug("Starting transaction for workBundle[{}]", workBundle.getBundleId());
        process(workBundle, STARTED);
    }

    /**
     * The work bundle parsed successfully
     *
     * @param workBundle the failed work bundle
     * @throws IOException if there is an issue
     */
    public void commit(WorkBundle workBundle) {
        if (!valid(workBundle))
            return;
        logger.debug("Parsing complete, updating transaction for workBundle[{}]", workBundle.getBundleId());
        process(workBundle, SUCCESS);
    }

    /**
     * The work bundle failed to parse successfully
     *
     * @param workBundle the failed work bundle
     * @throws IOException if there is an issue
     */
    public void fail(WorkBundle workBundle) {
        if (!valid(workBundle))
            return;
        logger.debug("Parsing failed, updating transaction for workBundle[{}]", workBundle.getBundleId());
        process(workBundle, FAILED);
    }

    /**
     * Start/Create a transaction on a file in the work bundle
     *
     * @param ibdo the base data object
     * @throws IOException if there is an issue
     */
    public void start(IBaseDataObject ibdo) throws IOException {
        if (!valid(ibdo))
            return;
        logger.debug("Starting transaction for workBundle[{}], ibdo[{},{}]", ibdo.getWorkBundleId(), ibdo.getInternalId(), ibdo.getFilename());
        handleStart(ibdo);
    }

    /**
     * Collect any info about the processed data and update the transaction
     *
     * @param ibdos a list of base data objects
     * @param params a list of parameters that are not in the ibdo
     * @throws IOException if there is an issue
     */
    public void update(List<IBaseDataObject> ibdos, Map<String, Object> params) throws IOException {
        IBaseDataObject ibdo = ibdos.get(0);
        if (!valid(ibdo))
            return;
        logger.debug("Updating transaction for workBundle[{}], ibdo[{},{}]", ibdo.getWorkBundleId(), ibdo.getInternalId(), ibdo.getFilename());
        handleUpdate(ibdo, params);
    }

    /**
     * Finish a transaction for a file
     *
     * @param ibdo the base data object
     * @throws IOException if there is an issue
     */
    public void commit(IBaseDataObject ibdo) throws IOException {
        if (!valid(ibdo))
            return;
        logger.debug("Completing transaction for workBundle[{}], ibdo[{},{}]", ibdo.getWorkBundleId(), ibdo.getInternalId(), ibdo.getFilename());
        handleComplete(ibdo, SUCCESS);
    }

    /**
     * Fail a transaction for a file
     *
     * @param ibdo the base data object
     * @throws IOException if there is an issue
     */
    public void fail(IBaseDataObject ibdo) throws IOException {
        if (!valid(ibdo))
            return;
        logger.error("Failing transaction for workBundle[{}], ibdo[{},{}]", ibdo.getWorkBundleId(), ibdo.getInternalId(), ibdo.getFilename());
        handleComplete(ibdo, FAILED);
    }

    /**
     * Startup initiated
     *
     * @throws IOException if there is an issue
     */
    public void startup() {
        if (!valid())
            return;

        try {
            logger.debug("Starting up transaction management, looking for orphaned transactions");
            handleStartup();
        } catch (Exception e) {
            shutdown = true;
            logger.error("Unable to process startup for transactions", e);
        }
    }

    /**
     * Shutdown initiated, so fail all current transactions
     *
     * @throws IOException if there is an issue
     */
    public void shutdown() {
        if (!valid())
            return;

        try {
            logger.debug("Shutdown initiated, failing current transactions");
            handleShutdown();
            shutdown = true;
        } catch (Exception e) {
            logger.error("Unable to process shutdown for transactions", e);
        }
    }

    /**
     * Creates a transaction object
     *
     * @param id the identifier of the transaction
     * @return the transaction object
     */
    protected Transaction create(String id) {
        Transaction tx = new Transaction(id);
        tx.addMetadata(META_PROCESSING_NODE, emissaryNode);
        return tx;
    }

    /**
     * Fix the input file path
     *
     * @param path path to the input file
     * @param prefix the common path
     * @return the clean file path
     */
    protected String fixFilePath(String path, String prefix) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(prefix)) {
            return path;
        }

        int index = path.lastIndexOf(prefix);
        int length = prefix.length() + (prefix.endsWith(File.separator) ? 0 : 1);
        return (index > -1) ? path.substring(index + length, path.length()) : path;
    }

    /**
     * Process the work bundle. The work bundle contains a list of file paths.
     *
     * @param workBundle the work bundle to process
     * @param status the current status for the transaction
     */
    protected void process(WorkBundle workBundle, Transaction.Status status) {
        workBundle.getFileNameList().forEach(file -> {
            try {
                process(workBundle, file, status);
            } catch (Exception e) {
                logger.error("There was an unexpected error processing transaction for WorkBundle:{}, status:{}",
                        workBundle.getBundleId(), status, e);
            }
        });
    }

    /**
     * Process a file out of the work bundle
     *
     * @param workBundle the work bundle to process
     * @param file a file path contained in the work bundle
     * @param status the current status for the transaction
     * @return the transaction object
     * @throws IOException if there is an issue creating/updating the transaction
     */
    protected void process(WorkBundle workBundle, String file, Transaction.Status status) throws IOException {

        String fixedPath = fixFilePath(file, inputFolder);
        String txid = Transaction.generateTransactionId(workBundle.getBundleId(), fixedPath);

        Transaction tx;
        if (status == STARTED || status == CREATED) {
            tx = create(txid);
            addMetadata(tx, workBundle, fixedPath);
            if (status == STARTED) {
                tx.start();
                manager.add(tx);
            }
            manager.log(tx);
        } else {
            tx = manager.get(txid);
            if (tx == null) {
                throw new IOException(String.format("Transaction{id:%s,file:%s,fileDate:%s} not found!!", txid, fixedPath));
            }
            update(tx, status);
            manager.update(tx);
            complete(tx);
        }
    }

    /**
     * Create a start a transaction based on an ibdo
     *
     * @param ibdo the base data object
     */
    protected void handleStart(IBaseDataObject ibdo) throws IOException {
        String fixedPath = fixFilePath(ibdo.getStringParameter(INPUT_FILENAME), pendingFolder);
        String ptxid = Transaction.generateTransactionId(ibdo.getWorkBundleId(), fixedPath);
        ibdo.setTransactionId(ptxid);

        // get the parent transaction
        Transaction parentTx = getParent(ibdo);
        Transaction tx = create(ibdo.getInternalId().toString());
        addMetadata(tx, ibdo, fixedPath);
        tx.start();
        parentTx.addTransaction(tx);
        manager.update(parentTx);
        manager.log(tx);
    }

    /**
     * Update a transaction with metadata
     *
     * @param ibdo the base data object
     * @param params additional params
     */
    protected void handleUpdate(IBaseDataObject ibdo, Map<String, Object> params) throws IOException {
        Transaction parentTx = getParent(ibdo);
        Transaction tx = get(ibdo, parentTx);
        params.entrySet().stream()
                .filter(e -> e.getKey().startsWith(CONTENT_URI) && e.getValue() != null)
                .forEach(e -> tx.addMetadata(e.getKey(), Objects.toString(e.getValue())));
        manager.update(parentTx);
        manager.log(tx);
    }

    /**
     * Process an ibdo
     *
     * @param ibdo the base data object to process
     * @param status status to set the transaction
     */
    protected void handleComplete(IBaseDataObject ibdo, Transaction.Status status) throws IOException {
        Transaction parentTx = getParent(ibdo);
        Transaction tx = get(ibdo, parentTx);

        ibdo.getParameters().entrySet().stream()
                .filter(e -> outputParams.contains(e.getKey()))
                .forEach(e -> tx.addMetadata(e.getKey(), e.getValue().stream().map(Objects::toString).collect(Collectors.toList())));

        update(tx, status);
        manager.update(parentTx);
        manager.log(tx);
        complete(parentTx);
    }

    /**
     * Handle shutdown
     */
    protected void handleStartup() throws IOException {
        logger.debug("Checking for orphaned transactions");
        manager.getAll().forEach(tx -> {
            try {
                tx.fail("Transaction orphaned due to shutdown or error");
                manager.log(tx);
            } catch (Exception e) {
                logger.error("There was an issue failing a transaction []", tx, e);
            }
        });

        // remove anything in the cache
        manager.removeAll();
    }

    /**
     * Handle shutdown
     */
    protected void handleShutdown() throws IOException {
        logger.debug("Checking for running transactions");
        try {
            manager.getAll().forEach(tx -> {
                tx.fail("Transaction unable to complete due to server shutdown");
                manager.log(tx);
            });
            manager.removeAll();
        } catch (Exception e) {
            // ignore
        }
        // finish shutdown
        manager.stop();
    }

    /**
     * Get the parent transaction from the transaction manager
     *
     * @param ibdo the ibdo holding the transaction id
     * @return the parent transaction
     * @throws IOException if the parent tx is not in the cache
     */
    protected Transaction getParent(IBaseDataObject ibdo) throws IOException {
        Transaction parentTx = manager.get(ibdo.getTransactionId());
        if (parentTx == null) {
            throw new IOException(String.format("Transaction{id:%s} not found for IBaseDataObject{internalId:%s}", ibdo.getTransactionId(),
                    ibdo.getInternalId()));
        }
        return parentTx;
    }

    /**
     * Get the transaction from the parent transaction list
     *
     * @param ibdo the ibdo holding the transaction id
     * @param parentTx the parent transaction holding the list of transactions
     * @return the parent transaction
     * @throws IOException if the parent tx is not in the cache
     */
    protected Transaction get(IBaseDataObject ibdo, Transaction parentTx) throws IOException {
        Transaction tx = parentTx.getTransaction(ibdo.getInternalId().toString());
        if (tx == null) {
            throw new IOException(String.format("Transaction{parentId:%s, id:%s} not found for IBaseDataObject{internalId:%s}",
                    ibdo.getTransactionId(), ibdo.getInternalId(), ibdo.getInternalId()));
        }
        return tx;
    }


    /**
     * Update a transaction status
     *
     * @param tx the transaction to update
     * @param status the new status
     */
    protected void update(Transaction tx, Transaction.Status status) {
        if (status == STARTED) {
            tx.start();
        } else if (status == FAILED) {
            tx.fail();
        } else if (status == TIMEOUT) {
            tx.timeout();
        } else if (status == SUCCESS) {
            tx.success();
        } else {
            throw new IllegalArgumentException("Transaction status:" + status + " could not be processed");
        }
        logger.debug("Transaction{id:{}} updated to {}", tx.getId(), status);
    }

    /**
     * Test to see if the transaction tree has completed processing, if so finish the transaction
     *
     * @param tx the parent transaction
     */
    protected void complete(Transaction tx) throws IOException {
        // check to see that this is a parent and the child transaction are complete
        if (tx.isComplete() && (!tx.hasTransactions() || (tx.hasTransactions() && tx.getTransactionCompleteCount() == tx.getTransactionCount()))) {
            if (tx.getStatus() == FAILED || tx.getTransactionFailureCount() > 0) {
                tx.fail();
                logger.error("Transaction failed [{}] ", tx);
            } else {
                tx.success();
            }

            manager.remove(tx);
            manager.log(tx);
        }
    }

    /**
     * Validate the request
     *
     * @param w the work bundle to validate
     * @return true if valid, false otherwise
     */
    private boolean valid(WorkBundle w) {
        return valid();
    }

    /**
     * Validate the request
     *
     * @param d the base data object to validate
     * @return true if valid, false otherwise
     */
    private boolean valid(IBaseDataObject d) {
        return valid() && StringUtils.isNotBlank(d.getStringParameter(INPUT_FILENAME));
    }

    /**
     * Validate the request
     *
     * @return true if valid, false otherwise
     */
    private boolean valid() {
        return active && !shutdown;
    }

    /**
     * Add metadata to the transaction
     *
     * @param tx the transaction object
     * @param workBundle the work bundle
     * @param filename the file path
     */
    private void addMetadata(Transaction tx, WorkBundle workBundle, String filename) {
        tx.addMetadata(META_BUNDLE_ID, workBundle.getBundleId());
        tx.addMetadata(META_PRIORITY, String.valueOf(workBundle.getPriority()));
        tx.addMetadata(META_FILE_NAME, filename);
    }

    /**
     * Add metadata to the transaction
     *
     * @param tx the transaction object
     * @param ibdo the base data object
     * @param filename the file path
     */
    private void addMetadata(Transaction tx, IBaseDataObject ibdo, String filename) {
        tx.addMetadata(META_PARENT_TX_ID, ibdo.getTransactionId());
        tx.addMetadata(META_FILE_NAME, filename);
        tx.addMetadata(META_SHORTNAME, ibdo.shortName());
        addParamToMetadata(tx, ibdo, FILE_DATE, META_FILE_DATE);
    }

    /**
     * Check the ibdo for a parameter
     *
     * @param tx the transaction object
     * @param ibdo the base data object
     * @param parameter the parameter in the ibdo
     * @param metadata the key for the new metadata
     */
    private void addParamToMetadata(Transaction tx, IBaseDataObject ibdo, String parameter, String metadata) {
        String paramValue = ibdo.getStringParameter(parameter);
        if (StringUtils.isNotBlank(paramValue)) {
            tx.addMetadata(metadata, paramValue);
        }
    }

}
