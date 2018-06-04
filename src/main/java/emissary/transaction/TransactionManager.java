package emissary.transaction;

import emissary.config.Configurator;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static emissary.transaction.Transaction.Status.FAILED;

public abstract class TransactionManager {

    protected static final Logger txLog = LoggerFactory.getLogger("transactions");

    public static final String NAMESPACE = "TransactionManager";
    protected static final long MAX_MEM = Runtime.getRuntime().maxMemory();
    protected static final long GB = 1073741824L;

    protected Configurator conf;
    protected int cacheSize;
    protected int cacheExpireTime;
    protected TimeUnit cacheExpireTimeUnit;

    /**
     * Configure the transaction manager
     *
     * @throws IOException if there is an issue
     */
    public abstract void configure() throws IOException;

    /**
     * Get the transaction map
     *
     * @return the map of transaction ids to transaction objects
     */
    protected abstract Map<String, Transaction> getTransactionsMap();

    /**
     * Get a transaction by id
     *
     * @param id the the id of the transaction
     * @return the transaction object
     * @throws IOException if there is an issue retrieving the transaction
     */
    public abstract Transaction get(String id) throws IOException;

    /**
     * Add a new transaction
     *
     * @param tx the transaction to save
     * @throws IOException if there is an issue inserting the transaction
     */
    public abstract void add(Transaction tx) throws IOException;

    /**
     * Update an existing transaction
     *
     * @param tx the transaction to save
     * @throws IOException if there is an issue updating the transaction
     */
    public abstract void update(Transaction tx) throws IOException;

    /**
     * Delete a transaction
     *
     * @param tx the transaction to save
     * @throws IOException if there is an issue removing the transaction
     */
    public abstract void remove(Transaction tx) throws IOException;

    /**
     * Delete all transactions
     *
     * @throws IOException if there is an issue removing the transactions
     */
    public abstract void removeAll() throws IOException;

    /**
     * Stop the transaction manager
     *
     * @throws IOException if there is an issue stopping the transaction manager
     */
    public abstract void stop() throws IOException;

    /**
     * Configure the transaction manager
     *
     * @param config the configuration object
     * @throws IOException if there is an issue
     *
     */
    public void configure(Configurator config) throws IOException {
        conf = config;
        cacheSize = conf.findIntEntry("CACHE_SIZE",
                Integer.getInteger("agent.poolsize", (((int) (MAX_MEM / GB) - 1) * 5) + 20));
        cacheExpireTime = conf.findIntEntry("CACHE_EXPIRE_TIME", 2);
        cacheExpireTimeUnit = TimeUnit.valueOf(conf.findStringEntry("CACHE_EXPIRE_TIME_UNIT", "HOURS"));
    }

    /**
     * Get all transactions
     *
     * @return a collection of current transactions
     * @throws IOException if there is an issue retrieving the transactions
     */
    public Collection<Transaction> getAll() throws IOException {
        validate();
        return Collections.unmodifiableCollection(getTransactionsMap().values());
    }

    /**
     * Log to the transaction log
     */
    public void log() {
        getTransactionsMap().values().forEach(tx -> tx.log(txLog));
    }

    /**
     * Log a transaction to the transaction log
     *
     * @param tx the transaction to log
     */
    public void log(Transaction tx) {
        if (tx == null) {
            return;
        }
        if (tx.getStatus() == FAILED) {
            txLog.error(tx.toJson());
        } else {
            txLog.info(tx.toJson());
        }
    }

    /**
     * Validate the id is not null or blank
     *
     * @param id the id to validate
     * @throws IOException if the id is not valid
     */
    protected void validateNotBlank(String id) throws IOException {
        validate();
        if (StringUtils.isBlank(id)) {
            throw new IOException("Id cannot be blank");
        }
    }

    /**
     * Validate the transaction is not null
     *
     * @param tx the transaction to validate
     * @throws IOException if the transaction is not valid
     */
    protected void validateNotNull(Transaction tx) throws IOException {
        validate();
        if (tx == null) {
            throw new IOException("Transaction cannot be null");
        }
    }

    /**
     * Validate the id is not currently cached
     *
     * @param id the id to validate
     * @throws IOException if the id already exists in the cache
     */
    protected void validateUnique(String id) throws IOException {
        validate();
        if (get(id) != null) {
            throw new IOException("Transaction [" + id + "] already exists");
        }
    }

    /**
     * Validate the id is cached
     *
     * @param id the id to validate
     * @throws IOException if the id does not exist in the cache
     */
    protected void validateExists(String id) throws IOException {
        validate();
        if (get(id) == null) {
            throw new IOException("Transaction [" + id + "] does not exist");
        }
    }

    /**
     * Validate
     *
     * @throws IOException if there is an issue
     */
    protected void validate() throws IOException {}
}
