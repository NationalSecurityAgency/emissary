package emissary.core;

import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;

/**
 * For hacking the move test. This agent forces a move even when place is in the namespace.
 */
public class BogusHDAgent extends HDMobileAgent {

    static final long serialVersionUID = -8044923045057998565L;

    public BogusHDAgent() {
        super();
    }

    /**
     * Create a new reusable Agent
     * 
     * @param threadGroup group we operate it
     * @param threadName symbolic name for this agent thread
     */
    public BogusHDAgent(final ThreadGroup threadGroup, final String threadName) {
        super(threadGroup, threadName);
    }

    /**
     * Get the next key from the directory with error handling Can return null if there is no place to handle the form
     * 
     * @param place the place we will use to access the directory
     * @param payload the current payload we care about
     * @return the fake Directory Entry (local always false)
     */
    @Override
    protected DirectoryEntry getNextKey(final IServiceProviderPlace place, final IBaseDataObject payload) {
        final DirectoryEntry de = super.getNextKey(place, payload);
        if (de == null) {
            return null;
        }

        // Compare host and port
        final DirectoryEntry lastVisited = payload.getLastPlaceVisited();
        if (lastVisited != null && de.getServiceHostURL().equals(lastVisited.getServiceHostURL())) {
            // Ok they really *are* local
            return de;
        }
        // They appear local due to being in same JVM but on are on different
        // host and port, so force them to be non-local
        return new BogusDirectoryEntry(de);
    }
}
