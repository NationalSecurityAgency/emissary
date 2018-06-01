package emissary.directory;

import java.util.Collection;
import java.util.List;

/**
 * Interface for a face that fits on the ItineraryFacet
 */
public interface ItineraryFace {

    /**
     * Produce list of types the facet will operate on This is the dataType::serviceType of interest (e.g. UNKNOWN::ID)
     */
    Collection<String> getTypes();

    /**
     * Analyze a dataType and selected Itinerary, possibly changinging the itinerary
     * 
     * @param dataType current type of the data
     * @param payload the data object being routed
     * @param itinerary current list of itinerary to be processed
     * @param entryMap map of what is registered in the directory
     */
    void process(String dataType, emissary.core.IBaseDataObject payload, List<DirectoryEntry> itinerary, DirectoryEntryMap entryMap);
}
