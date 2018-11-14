package emissary.directory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.server.mvc.adapters.HeartbeatAdapter;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facility for directory instances to check up on each other by sending a heartbeat message
 */
public class HeartbeatManager {
    // Our logger
    protected static final Logger logger = LoggerFactory.getLogger(HeartbeatManager.class);

    // parameter constants
    public static final String FROM_PLACE_NAME = "hbf";
    public static final String TO_PLACE_NAME = "hbt";
    public static final String BAD_RESPOSNE = "Bad request -> status: 500";


    /** The timer drives the tasks and scheduling of heartbeat pings */
    protected Timer timer = null;

    /** Directory this instance acts on behalf of */
    protected String thisDirectory;

    /** Default delay seconds before timer starts {@value} */
    public static final int DEFAULT_INITIAL_DELAY_SECONDS = 60;

    /**
     * Configured value before timer starts in seconds, default is {@value #DEFAULT_INITIAL_DELAY_SECONDS}
     */
    protected int initialDelaySeconds = DEFAULT_INITIAL_DELAY_SECONDS;

    /** Default delay between timer runs {@value} */
    public static final int DEFAULT_INTERVAL_SECONDS = 30;

    /**
     * Configured value between timer runs in seconds, default is {@value #DEFAULT_INTERVAL_SECONDS}
     */
    protected int intervalSeconds = DEFAULT_INTERVAL_SECONDS;

    /** Number of consecutive failures to trigger notice */
    protected int failThreshold = 3;

    /** Number of consecutive failures to trigger permanent failure notice */
    protected int permanentFailThreshold = 20;

    /** Status value for callers to use when setting initially healthy */
    public static final boolean IS_ALIVE = true;

    /** Status value for callers to use when setting initially not healthy */
    public static final boolean NO_CONTACT = false;

    /** The remote directories we are checking on and their health */
    protected Map<String, Health> directories = new ConcurrentHashMap<String, Health>(100, 0.8f, 3);

    // /**
    // * Setup to manage heartbeats to remote directories
    // *
    // * @param directoryKey Key for directory I act on behalf of
    // */
    // public HeartbeatManager(final String directoryKey) {
    // this(directoryKey, null, DEFAULT_INITIAL_DELAY_SECONDS, DEFAULT_INTERVAL_SECONDS);
    // }

    /**
     * Setup to manage heartbeats to remote directories
     *
     * @param directoryKey Key for directory I act on behalf of
     * @param initialDelaySeconds seconds to wait before initial timer task
     * @param intervalSeconds how often the timeer task kicks off
     */
    public HeartbeatManager(final String directoryKey, final int initialDelaySeconds, final int intervalSeconds) {
        this(directoryKey, null, initialDelaySeconds, intervalSeconds);
    }

    /**
     * Setup to manage heartbeats to remote directories
     *
     * @param directoryKey Key for directory I act on behalf of
     * @param dirList list of directory keys for remote directories
     * @param initialDelaySeconds seconds to wait before initial timer task
     * @param intervalSeconds how often the timeer task kicks off
     */
    public HeartbeatManager(final String directoryKey, final List<String> dirList, final int initialDelaySeconds, final int intervalSeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
        this.intervalSeconds = intervalSeconds;

        logger.debug("Starting with initialDelay=" + initialDelaySeconds + ", interval=" + intervalSeconds);

        // new daemon timer
        this.timer = new Timer("HeartbeatManager", true);

        // Save directory key
        this.thisDirectory = directoryKey;

        // Save each directory in dirList along with good
        // starting health value
        if (dirList != null) {
            for (final String key : dirList) {
                addRemoteDirectory(key);
            }
        }

        // "smooth" execution every 30 seconds starting in 2 minutes
        this.timer.schedule(new HeartbeatTask(), new Date(System.currentTimeMillis() + (this.initialDelaySeconds * 1000L)),
                (this.intervalSeconds * 1000L));
    }

    /**
     * Set the failure threshold
     */
    public void setFailThreshold(final int t) {
        this.failThreshold = t;
        logger.debug("Set new fail threshold to " + t);
    }

    /**
     * Set the second failure threshold
     */
    public void setPermanentFailThreshold(final int t) {
        this.permanentFailThreshold = t;
        logger.debug("Set new permanent fail threshold to " + t);
    }

    /**
     * Shutdown processing
     */
    public void shutDown() {
        this.timer.cancel();
    }

    /**
     * Add another directory to monitor
     *
     * @param key four-tuple for the remote directory
     */
    public void addRemoteDirectory(final String key) {
        addRemoteDirectory(key, true);
    }

    /**
     * Add another directory to monitor with initial status
     *
     * @param key four-tuple for the remote directory
     * @param isAlive initial status
     */
    public void addRemoteDirectory(final String key, final boolean isAlive) {
        // Skip if on same JVM
        if (!KeyManipulator.isLocalTo(this.thisDirectory, key)) {
            final String dkey = KeyManipulator.getDefaultDirectoryKey(key);
            this.directories.put(dkey, new Health(isAlive, "Initial status"));
            logger.debug("Added remote " + dkey + " with initial status " + isAlive + " now monitoring " + this.directories.size()
                    + " remote directories");
        } else {
            logger.debug("Skipping local directory " + key + ", is not remote");
        }
    }

    /**
     * Remove a directory from the monitor list
     *
     * @param key four-tuple for the remote directory
     */
    public void removeRemoteDirectory(final String key) {
        this.directories.remove(KeyManipulator.getDefaultDirectoryKey(key));
    }

    /**
     * Access to see if remote is healthy
     *
     * @param key four-tuple key for remote directory
     */
    public boolean isHealthy(final String key) {
        final String dirKey = KeyManipulator.getDefaultDirectoryKey(key);
        final Health val = this.directories.get(dirKey);
        if (val != null) {
            return val.isHealthy();
        }
        return false;
    }

    /**
     * Access to see if remote is alive
     *
     * @param key four-tuple key for remote directory
     */
    public boolean isAlive(final String key) {
        final String dirKey = KeyManipulator.getDefaultDirectoryKey(key);
        final Health val = this.directories.get(dirKey);
        if (val != null) {
            return val.isAlive();
        }
        return false;
    }

    /**
     * Externally set the status. Does not call trigger
     *
     * @param key directory to set status for
     * @param status new status
     * @param reason the exception message on bad status
     */
    void setHealthStatus(final String key, final boolean status, final String reason) {
        final String dirKey = KeyManipulator.getDefaultDirectoryKey(key);
        final Health v = this.directories.get(dirKey);
        if (v == null) {
            logger.error("Not monitoring directory " + dirKey);
            return;
        }

        logger.debug("Externally setting " + status + " status for " + key + " - " + reason);

        v.setStatus(status, reason);
    }

    /**
     * Set health status and call the trigger on transition
     *
     * @param key key for directory
     * @param status new status
     * @param reason the exception message on bad status
     */
    protected void healthReport(final String key, final boolean status, final String reason) {
        final String dirKey = KeyManipulator.getDefaultDirectoryKey(key);
        final Health v = this.directories.get(dirKey);
        if (v == null) {
            logger.error("Not monitoring directory " + dirKey);
            return;
        }

        final boolean wasAlive = v.isAlive();
        final boolean wasHealthy = v.isHealthy();

        v.addReport(status, reason);

        final boolean isAlive = v.isAlive();
        final boolean isHealthy = v.isHealthy();

        if (logger.isDebugEnabled()) {
            logger.debug("Reporting on " + key + " status=" + status + ", wasAlive/Healthy=" + wasAlive + "/" + wasHealthy + ", isAlive/Healthy="
                    + isAlive + "/" + isHealthy);
        }

        if (wasAlive && !isAlive) {
            // We had a permanent failure.
            takeFailureAction(key, true);
        } else if (wasHealthy && !isHealthy) {
            // We had a negative transition.
            takeFailureAction(key, false);
        } else if (!wasHealthy && isHealthy) {
            // We had a positive transition.
            takeSuccessAction(key);
        }
    }

    /**
     * Notify our directory that there was a falure
     *
     * @param key string key of the directory that failed
     */
    public void takeFailureAction(final String key, final boolean permanent) {
        final String myKey = KeyManipulator.getServiceLocation(this.thisDirectory);
        try {
            final IRemoteDirectory d = (IRemoteDirectory) Namespace.lookup(myKey);
            final int count = d.irdFailRemoteDirectory(key, permanent);
            logger.info("Notified " + myKey + " of failed directory " + key + (permanent ? " permanently!" : "") + ", " + count + " keys removed");
        } catch (NamespaceException ne) {
            logger.error("Tried to fail a remote directory " + key + " but cannot look up my own directory using " + myKey, ne);
        }
    }

    /**
     * Notify our directory that a directory has been contacted If parent considers this a rdv or rly host they this could
     * initiate a zone transfer or other action
     *
     * @param key string key of the directory that was contacted
     */
    void takeSuccessAction(final String key) {
        final String myKey = KeyManipulator.getServiceLocation(this.thisDirectory);
        try {
            final DirectoryPlace d = (DirectoryPlace) Namespace.lookup(myKey);
            logger.info("Notifying " + myKey + " of re-established contact with " + key);
            d.contactedRemoteDirectory(key);
        } catch (NamespaceException ne) {
            logger.error("Tried to reestablish a remote directory " + key + " but cannot look up my own directory using " + myKey, ne);
        }
    }


    /**
     * The Task thread, monitors all remote directories
     */
    class HeartbeatTask extends TimerTask {
        @Override
        public void run() {
            try {
                logger.debug("Running timer task on " + HeartbeatManager.this.directories.size() + " directories");
                for (final String dir : HeartbeatManager.this.directories.keySet()) {
                    heartbeat(dir);
                }
                logger.debug("Ending the HeartbeatTask run method");
            } catch (Exception e) {
                logger.error("Unexpected problem in heartbeat timer", e);
            }
        }
    }

    /**
     * Send a heartbeat message to the directory represented by key and take follow-on actions as appropriate Called from
     * the timer task normally, but can be called externally by the impatient
     *
     * @see emissary.directory.DirectoryPlace#heartbeatRemoteDirectory(String)
     * @param key key representing the directory to heartbeat
     * @return true if the directory referenced by key is up
     */
    public final boolean heartbeat(final String key) {
        boolean isup = false;
        try {
            logger.debug("Sending heartbeat msg to " + key);
            EmissaryResponse response = getHeartbeat(this.thisDirectory, key);
            if (response.getStatus() == 200) {
                healthReport(key, true, response.getContentString());
                isup = true;
            } else {
                healthReport(key, false, response.getContentString());
                isup = false;
            }
        } catch (Exception e) {
            logger.error("Cannot perform heartbeat", e);
            healthReport(key, false, e.getMessage());
            isup = false;
        }
        return isup;
    }

    public static EmissaryResponse getHeartbeat(String fromPlace, String toPlace) {
        return getHeartbeat(fromPlace, toPlace, new EmissaryClient());
    }

    public static EmissaryResponse getHeartbeat(String fromPlace, String toPlace, EmissaryClient client) {
        final String directoryUrl = KeyManipulator.getServiceHostURL(toPlace);
        final HttpPost method = new HttpPost(directoryUrl + EmissaryClient.CONTEXT + "/Heartbeat.action");
        final String loc = KeyManipulator.getServiceLocation(toPlace);

        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(HeartbeatAdapter.FROM_PLACE_NAME, fromPlace));
        nvps.add(new BasicNameValuePair(HeartbeatAdapter.TO_PLACE_NAME, loc));
        method.setEntity(new UrlEncodedFormEntity(nvps, java.nio.charset.Charset.defaultCharset()));

        return client.send(method);
    }


    /**
     * Holder class for the health information for a single remote directory
     */
    class Health {
        // Count consecutive failures
        private int failCounter = 0;

        private String lastMessage;

        /**
         * Create a new Health object with the specified status and msg
         *
         * @param isAlive true if the initial status is no failures
         * @param msg the initial msg value
         */
        public Health(final boolean isAlive, final String msg) {
            setStatus(isAlive, msg);
        }

        /**
         * Accumulate another heartbeat result
         *
         * @param v the most recent status
         * @param msg the most recent message
         */
        public void addReport(final boolean v, final String msg) {
            this.lastMessage = msg;
            if (v) {
                this.failCounter = 0;
            } else {
                this.failCounter++;
            }
        }

        /**
         * Set the status now
         *
         * @param isAlive false means permanent failure indicated
         * @param message message to asocciate with this statsu
         */
        void setStatus(final boolean isAlive, final String message) {
            if (!isAlive) {
                this.failCounter = HeartbeatManager.this.permanentFailThreshold;
                this.lastMessage = message;
            } else {
                this.failCounter = 0;
                this.lastMessage = message;
            }
        }

        /**
         * Report our health status
         *
         * @return true if failed less than threshold times
         */
        public boolean isHealthy() {
            return this.failCounter < HeartbeatManager.this.failThreshold;
        }

        /**
         * Report our aliveness status
         *
         * @return true if failed less than permanent threshold times
         */
        public boolean isAlive() {
            return this.failCounter < HeartbeatManager.this.permanentFailThreshold;
        }

        /**
         * Access to the last saved message
         */
        public String getLastMessage() {
            return this.lastMessage;
        }
    }
}
