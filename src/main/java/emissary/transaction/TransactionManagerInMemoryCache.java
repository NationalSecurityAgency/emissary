package emissary.transaction;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentMap;

public class TransactionManagerInMemoryCache extends TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Cache<String, Transaction> transactions;

    /**
     * {@inheritDoc}
     */
    @Override
    public void configure() throws IOException {
        configure(ConfigUtil.getConfigInfo(TransactionManagerInMemoryCache.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configure(Configurator config) throws IOException {
        super.configure(config);
        transactions = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterWrite(cacheExpireTime, cacheExpireTimeUnit)
                .removalListener(removal -> {
                    Transaction tx = (Transaction) removal.getValue();
                    if (tx != null) {
                        if (removal.getCause() == RemovalCause.SIZE) {
                            tx.fail("Too many transactions");
                            log(tx);
                        } else if (removal.getCause() == RemovalCause.EXPIRED) {
                            tx.timeout();
                            log(tx);
                        }
                    }
                })
                .build();

        Namespace.bind(NAMESPACE, this);
        log.info("Transaction manager ready");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ConcurrentMap<String, Transaction> getTransactionsMap() {
        return transactions.asMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transaction get(String id) throws IOException {
        validateNotBlank(id);
        return transactions.getIfPresent(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(Transaction tx) throws IOException {
        validateNotNull(tx);
        validateUnique(tx.getId());
        transactions.put(tx.getId(), tx);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(Transaction tx) throws IOException {
        validateNotNull(tx);
        validateExists(tx.getId());
        transactions.put(tx.getId(), tx);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(Transaction tx) throws IOException {
        validateNotNull(tx);
        validateNotBlank(tx.getId());
        transactions.invalidate(tx.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAll() {
        transactions.invalidateAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        removeAll();
        transactions.cleanUp();
        log.info("Transaction manager stopped");
    }
}
