package emissary.kff;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.util.Hexl;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.ConnectionFactoryBuilder.Protocol;
import net.spy.memcached.FailureMode;
import net.spy.memcached.MemcachedClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KffMemcached checks Emissary hashes against a set of external memcached servers. If a given Emissary hash does not
 * hit in memcached, it is added to the memcached. The value stored is however the input is identified (notionally, some
 * type of id). Only one type of hash can be checked against memcached (i.e. SHA-1...not SHA-1 <i>and</i> SHA-256). See
 * the PREF_ALG configuration option.
 *
 * If the option MEMCACHED_STORE_ID_DUPE is set to true, if an Emissary hash already exists in memcached, the id will
 * also be loaded into memcached as a <i>key</i>. The purpose of this is for other follow-on processes (non-Emissary) to
 * query memcached and determine if a given id is a duplicate (i.e. if it is present).
 *
 * If a server goes down, this code will wait and try to reconnect with increasingly long intervals between retries.
 * Under this mode of operation, it is expected someone will bring the server back online.
 *
 * Configuration file options are:
 *
 * MEMCACHED_SERVER: one line for each server, optional : separated port MEMCACHED_AGEOFF: how long to hold an object in
 * memcached before expiring MEMCACHED_OP_TIMEOUT_MILLIS: how long to wait before timing out a memcached operation
 * MEMCACHED_IGNORE_VALUE_PATTERN: do not store values that contain this pattern (non-regexO MEMCACHED_FAILURE_MODE:
 * what to do in case of server failure MEMCACHED_STORE_ID_DUPE: boolean to store the id if it's hash is already
 * contained in memcached PREF_ALG: Which Emissary hash to use as the key stored in memcached
 */
public class KffMemcached implements KffFilter {

    /**
     * Logger
     */
    private Logger logger;

    /**
     * The hash to use as the key
     */
    protected String preferredAlgorithm = "SHA-1";

    /**
     * String logical name for this filter
     */
    protected String filterName = "UNKNOWN";

    /**
     * Filter type
     */
    protected FilterType ftype = FilterType.Unknown;

    /**
     * The age-off in the memcached client
     */
    protected int ageoff = 86400;

    /**
     * The timeout on any given network operation in milliseconds
     */
    protected long opTimeoutMillis = 2500L;

    /**
     * What to do in case there is a failure contacting a given server
     */
    protected FailureMode failMode = FailureMode.Cancel;

    /**
     * Do not store values that contain these substrings exactly (this is not treated as a regex)
     */
    protected Set<String> ignorePatterns = null;

    /**
     * If this is set to true, if an Emissary hash already exists in memcached, the id will also be loaded into memcached as
     * a <i>key</i>. The purpose of this is for other follow-on processes (non-Emissary) to query memcached and determine if
     * a given id is a duplicate (i.e. if it is present).
     */
    protected boolean storeIdDupe = false;

    /**
     * Whether or not to use the memcached binary protocol
     */
    protected boolean useBinaryProtocol = false;

    /**
     * A handle to the set of servers
     */
    protected MemcachedClient client;

    /**
     *
     * @param filename Unused
     * @param filterName Name of the filter (typically sent in by KffChainLoader)
     * @param ftype Filter type (again, sent in by KffChainLoader)
     * @throws IOException is thrown if either the file cannot be read of memcached cannot be contacted
     */
    public KffMemcached(String filename, String filterName, FilterType ftype) throws IOException {
        this(filename, filterName, ftype, null);

    }

    /**
     *
     * @param testIdWithSpaces Unused
     * @param filterName Name of the filter (typically sent in by KffChainLoader)
     * @param duplicate Filter type (again, sent in by KffChainLoader)
     * @param testClient Memcached client to be used if specified (will instantiate a client if null)
     * @throws IOException is thrown if either the file cannot be read of memcached cannot be contacted
     */
    public KffMemcached(String testIdWithSpaces, String filterName, FilterType duplicate, MemcachedClient testClient) throws IOException {
        // Set logger to run time class
        logger = LoggerFactory.getLogger(this.getClass().getName());
        // Set the logger impl to use log4j
        System.setProperty("net.spy.log.LoggerImpl", "net.spy.memcached.compat.log.Log4JLogger");

        // testIdWithSpaces is not used
        this.ftype = duplicate;
        this.filterName = filterName;

        Configurator configG = ConfigUtil.getConfigInfo(KffMemcached.class);

        // Load up the list of servers
        Set<String> serversFromConfig = configG.findEntriesAsSet("MEMCACHED_SERVER");
        List<InetSocketAddress> servers = new LinkedList<InetSocketAddress>();
        for (String serverFromConfig : serversFromConfig) {
            // Transform to an InetSocketAddress
            if (serverFromConfig.contains(":")) {
                String[] serverTokens = serverFromConfig.split(":");
                String host = serverTokens[0];
                int port = Integer.parseInt(serverTokens[1]);
                servers.add(new InetSocketAddress(host, port));
            } else {
                // In this case, assume port is 11211
                servers.add(new InetSocketAddress(serverFromConfig, 11211));
            }
        }

        logger.debug("The following memcached servers are configured:");
        for (InetSocketAddress server : servers) {
            logger.debug("Server configured: " + server);
        }

        // Default to 24 hours timeout
        ageoff = configG.findIntEntry("MEMCACHED_AGEOFF", 86400);

        // Set the preferred algorithm
        preferredAlgorithm = configG.findStringEntry("PREF_ALG");

        // Set the preferred algorithm
        ignorePatterns = configG.findEntriesAsSet("MEMCACHED_IGNORE_VALUE_PATTERN");

        // Whether or not to keep track of dupe IDs in memcached
        storeIdDupe = configG.findBooleanEntry("MEMCACHED_STORE_ID_DUPE", false);

        // Set whether to use the binary protocol or not
        useBinaryProtocol = configG.findBooleanEntry("MEMCACHED_USE_BINARY_PROTOCOL", useBinaryProtocol);

        // Set the operation timeout
        opTimeoutMillis = configG.findLongEntry("MEMCACHED_OP_TIMEOUT_MILLIS", opTimeoutMillis);

        String failModeAsString = configG.findStringEntry("MEMCACHED_FAILURE_MODE", "Cancel");
        if (failModeAsString.equalsIgnoreCase("cancel")) {
            failMode = FailureMode.Cancel;
        } else if (failModeAsString.equalsIgnoreCase("retry")) {
            failMode = FailureMode.Retry;
        }

        // Finally, setup the client. ConnectionFactoryBuilder ultimately
        // creates a DefaultConnectionFactory with the values set below
        ConnectionFactoryBuilder cfb = new ConnectionFactoryBuilder();

        // There are a number of options that can be set here. Documenting
        // all of them and why or why not they are being set

        // cfb.setAuthDescriptor(null); // Not using auth
        cfb.setDaemon(true); // Just to keep the process from hanging
        cfb.setFailureMode(failMode); // How to handle operations when they fail
        // cfb.setHashAlg(null); // We assume a fixed server set here so we do not modify the default hash algorithm
        // cfb.setInitialObservers(null); // This is where we would hook callbacks to manage the server set
        // cfb.setLocatorType(null); // Use default
        cfb.setMaxReconnectDelay(60); // At most, wait 1 minute for attempting to reconnect to a server
        // cfb.setOpFact(null); // Use default
        // cfb.setOpQueueFactory(null); // Use default
        // cfb.setOpQueueMaxBlockTime(0); // Use default
        cfb.setOpTimeout(opTimeoutMillis); // Use the same for the connection as the concurrent Future object
        // cfb.setProtocol(null); // Use ASCII protocol (default)
        // cfb.setReadBufferSize(0); // Use default
        // cfb.setReadOpQueueFactory(null); // Use default
        // cfb.setShouldOptimize(false); // Use default
        // cfb.setTimeoutExceptionThreshold(3); // Setting this is only useful if you are going to shutdown the bad
        // nodes
        // cfb.setTranscoder(null); // Use default
        // cfb.setUseNagleAlgorithm(false); // Use default
        // cfb.setWriteOpQueueFactory(null); // Use default

        if (useBinaryProtocol) {
            cfb.setProtocol(Protocol.BINARY);
        }

        if (testClient == null) {
            client = new MemcachedClient(cfb.build(), servers);
        } else {
            client = testClient;
        }

        logger.debug(client.toString());
    }

    /**
     * Contact the memcached server and lookup the hash. If it is found, then return true. If it is not found, store it and
     * return false. If it matches a special ignore pattern, return false. If the server is down or any other problems throw
     * an exception
     */
    @Override
    public boolean check(String id, ChecksumResults sums) throws Exception {

        if (sums == null) {
            throw new Exception("Poorly formed input to check() in sums");
        }

        if ((id == null) || (id.length() == 0)) {
            throw new Exception("Poorly formed input to check() in fname");
        }

        // Ignore any IDs that contain the ignorePatterns string verbatim
        if (ignorePatterns != null) {
            // Loop through all patterns to see if any match
            for (String ignorePattern : ignorePatterns) {
                if (id.contains(ignorePattern)) {
                    return false;
                }
            }
        }

        byte[] hash = sums.getHash(preferredAlgorithm);

        if ((hash == null) || (hash.length == 0)) {
            throw new Exception("Poorly formed input to check() in hash");
        }

        String key = Hexl.toUnformattedHexString(hash);

        // Send the query
        Future<Object> future = client.asyncGet(key);
        Object result;

        // Let the TimeoutException propagate up
        result = future.get(opTimeoutMillis, TimeUnit.MILLISECONDS);

        if (result != null) {
            if (storeIdDupe) {
                if (!((String) result).equals(id)) {
                    // As long as the id is not the same as what was already stored, then
                    // store it on its own
                    client.set(id, ageoff, key);
                    logger.debug("Storing duplicate Id: " + id + " with value (hash) " + key);
                }
            }
            logger.debug("Found key: " + key + " with value " + (String) result);
            // Found the key
            return true;
        }
        logger.debug("Did not find key: " + key);
        // Did not find the key...store it and move on
        client.set(key, ageoff, id);
        return false;


    }

    public String getPreferredAlgorithm() {
        return preferredAlgorithm;
    }

    public void setPreferredAlgorithm(String preferredAlgorithm) {
        this.preferredAlgorithm = preferredAlgorithm;
    }

    @Override
    public String getName() {
        return filterName;
    }

    @Override
    public FilterType getFilterType() {
        return ftype;
    }

}
