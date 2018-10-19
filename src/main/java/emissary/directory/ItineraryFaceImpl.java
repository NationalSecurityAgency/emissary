package emissary.directory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import emissary.core.IBaseDataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic extensible do-nothing implementation of an ItineraryFace
 */
public class ItineraryFaceImpl implements ItineraryFace {
    // Shared logger
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // What types this facet registers for
    protected Set<String> myTypes = new HashSet<String>();

    /**
     * Send up the list of datatypes that we care about
     * 
     * @return collection of string data types formed from SERVICE_NAME::SERVICE_TYPE (e.g. UNKNWON::ID)
     */
    @Override
    public Collection<String> getTypes() {
        return new HashSet<String>(this.myTypes);
        // return (Collection<String>)((HashSet)myTypes).clone();
    }

    /**
     * Register a type we want to process
     * 
     * @param type the data type formed from SERVICE_NAME::SERVICE_TYPE (e.g. UNKNWON::ID)
     */
    protected void registerType(final String type) {
        this.myTypes.add(type);
    }

    /**
     * Determine is a type is registered
     * 
     * @param type the data type to check
     * @return true iff registered
     */
    public boolean isRegistered(final String type) {
        return this.myTypes.contains(type);
    }

    /**
     * Retrieve last item in current itinerary
     * 
     * @param itinerary list of DirectoryEntry items
     * @return DirectoryEntry of last item
     */
    public DirectoryEntry lastStep(final List<DirectoryEntry> itinerary) {
        if ((itinerary == null) || itinerary.isEmpty()) {
            return null;
        } else {
            return itinerary.get(itinerary.size() - 1);
        }
    }

    /**
     * Try to select a more expensive place specified by key than the sde already selected and on the same machine as the
     * specified sde, or any other one if none.
     *
     * Caller might use this to replace a service with a more expensive one, or to chain two services that handle the same
     * type in some other circumstance.
     *
     * @param sde the place already on the itinerary
     * @param entries the Directory entry map
     * @param key any portion of the current form to match, will be wildcarded like a normal directory lookup
     * @return SDE for the place chosen or null if none to choose
     */
    protected DirectoryEntry select(final DirectoryEntry sde, final DirectoryEntryMap entries, final String key) {
        return select(sde, entries, key, sde.getExpense() + 1, Integer.MAX_VALUE);
    }

    /**
     * Try to select a place specified by key whose expense falls within the specified range. Prefer a place on the same
     * machine as the specified sde, or any other machine if none on same machine.
     *
     * @param sde the place already on the itinerary so we can prefer the current host over any other
     * @param entries the Directory entry map
     * @param key any portion of the current form to match, will be wildcarded like a normal directory lookup
     * @param minExpense minimum expense for service to be chosen
     * @param maxExpense maximum expense for service to be chosen
     * @return SDE for the place chosen or null if none to choose
     */
    protected DirectoryEntry select(final DirectoryEntry sde, final DirectoryEntryMap entries, final String key, final int minExpense,
            final int maxExpense) {
        // The answer to be returned
        DirectoryEntry possible = null;

        // Check contract
        if (sde == null || entries == null || key == null || entries.size() == 0 || key.length() == 0 || minExpense > maxExpense) {
            this.logger.error("Cannot process arguments in select()");
            return possible;
        }

        // Wildcard incoming key
        final DirectoryEntryList d = WildcardEntry.getWildcardedEntry(key, entries);

        this.logger.debug("List based on " + key + " has size " + (d != null ? d.size() : -1));

        // Get host:port of incoming SDE
        final String desiredHost = sde.getServiceHostURL();

        for (final DirectoryEntry entry : d) {
            // Look in the specified range of expense
            if (minExpense <= entry.getExpense() && entry.getExpense() <= maxExpense) {
                // Record a possible hit
                possible = entry;

                // Found a hit on same host, we are done
                if (entry.getServiceHostURL().equals(desiredHost)) {
                    this.logger.debug("Sticking with " + entry + " since it is in cost range and on desired host");
                    break;
                }
            } else {
                this.logger.debug("Skipping entry " + entry + " not in cost range");
            }
        }

        if (possible != null) {
            this.logger.debug("Algorithm chose " + possible.getKey() + " based on " + sde.getKey() + " and type " + key);
        } else {
            this.logger.debug("Algorithm returning null");
        }

        return possible;
    }

    /**
     * Do nothing implementation
     * 
     * @param dataType current type of the data
     * @param payload the data object being routed
     * @param itinerary current list of itinerary to be processed
     * @param entryMap map of what is registered in the directory
     */
    @Override
    public void process(final String dataType, final IBaseDataObject payload, final List<DirectoryEntry> itinerary,
            final DirectoryEntryMap entryMap) {
        this.logger.debug("Do nothing process method called for dataType " + dataType);
    }
}
