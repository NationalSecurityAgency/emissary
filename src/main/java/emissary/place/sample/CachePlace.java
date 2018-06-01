package emissary.place.sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

/**
 * This place just keeps a reference to the last 10 payload objects it has seen. The number is configurable. It allows
 * processed to look back over a group of payloads.
 */
public class CachePlace extends ServiceProviderPlace {
    protected int cacheSize = 10;
    protected List<IBaseDataObject> cache;

    /**
     * The remote constructor
     */
    public CachePlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * The static standalone (test) constructor
     */
    public CachePlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestCachePlace.foo.com:8003");
        configurePlace();
    }

    /**
     * Set up place specific information from the config file, load JNI, etc.
     */
    private void configurePlace() {
        // Set configuration items from ToLowerPlace.cfg
        cacheSize = configG.findIntEntry("CACHE_SIZE", cacheSize);
        cache = new ArrayList<IBaseDataObject>(cacheSize);
    }

    /**
     * Consume a DataObject, and return a transformed one.
     */
    @Override
    public synchronized void process(IBaseDataObject d) {
        cache.add(d);
        logger.debug("added payload, size now " + cache.size());

        // Remove oldest if over size limit
        if (cache.size() > cacheSize) {
            IBaseDataObject evicted = cache.remove(0);
            evictedPayload(evicted);
        }
    }

    protected void evictedPayload(IBaseDataObject d) {
        logger.debug("Evicted payload " + d);
    }

    public int getCacheLimit() {
        return cacheSize;
    }

    public int getCacheSize() {
        return cache.size();
    }

    public synchronized IBaseDataObject pop() {
        if (cache.size() > 0) {
            return cache.remove(0);
        }
        return null;
    }

    @Override
    public void shutDown() {
        logger.debug("Removing " + cache.size() + " items in shutdown");
        while (cache.size() > 0) {
            cache.remove(0);
        }
        super.shutDown();
    }

}
