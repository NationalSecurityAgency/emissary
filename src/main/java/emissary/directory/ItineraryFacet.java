package emissary.directory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.core.Facet;
import emissary.core.Factory;
import emissary.core.IAggregator;
import emissary.core.IBaseDataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of Faces that work in conjunction with the DirectoryPlace to optimize or manipulate complex agent
 * itineraries
 */
public class ItineraryFacet extends Facet {
    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(ItineraryFacet.class);

    // Service type to List of class instance map
    Map<String, List<ItineraryFace>> faces = new HashMap<String, List<ItineraryFace>>();

    // Name this facet
    public static final String ITINERARY_FACET_NAME = "itinerary";

    /**
     * Create without configuration info
     */
    public ItineraryFacet() {
        setName(ITINERARY_FACET_NAME);
    }

    /**
     * Create and configure
     */
    public ItineraryFacet(final Configurator configG) {
        this();
        reconfigure(configG);
    }

    /**
     * Reconfigure the itinerary facet
     */
    protected void reconfigure(final Configurator configG) {

        // Place to store the new faces
        final Map<String, List<ItineraryFace>> newFaces = new HashMap<String, List<ItineraryFace>>();

        // Get the faces and build them
        final List<ConfigEntry> l = configG.findStringMatchEntries("FACET");
        logger.debug("Found " + l.size() + " FACET entries to configure");
        for (final ConfigEntry ce : l) {
            final String faceName = ce.getValue();
            final ItineraryFace f = createFace(faceName);
            register(f, newFaces);
        }

        // Save them to the real map
        this.faces = newFaces;
        logger.debug("We have " + this.faces.size() + " facet thinker mappings");
    }

    /**
     * Allow the face to register all the data types it wants to have a look at
     * 
     * @param f the face instance
     * @param m mapping of datatype to List of face instance
     */
    protected void register(final ItineraryFace f, final Map<String, List<ItineraryFace>> m) {
        if (f == null || m == null) {
            return;
        }
        for (final String dtype : f.getTypes()) {
            List<ItineraryFace> l = m.get(dtype);
            if (l == null) {
                l = new ArrayList<ItineraryFace>();
                m.put(dtype, l);
            }
            l.add(f);
            logger.debug("Face registering mapping " + dtype + " for " + f.getClass().getName());
        }
    }

    /**
     * Instantiate the specified face using the factory method
     * 
     * @param clazz full class name of the facet implementation
     * @return instance of an ItineraryFace
     */
    protected ItineraryFace createFace(final String clazz) {

        ItineraryFace f = null;
        try {
            f = (ItineraryFace) Factory.create(clazz);
            logger.debug("Created face " + clazz);
        } catch (Exception ex) {
            logger.warn("Unable to create ItineraryFace " + clazz, ex);
        }
        return f;
    }

    /**
     * Think about the specified itinerary and let any faces that have registered for the current data type have a look at
     * it.
     * 
     * @param dataType the current type of the data This is the dataType::serviceType of interest (e.g. UNKNOWN::ID)
     * @param payload the data object being routed
     * @param itinerary list of keys selected so far
     * @param entryMap map of what the directory has registered
     */
    public void thinkOn(final String dataType, final IBaseDataObject payload, final List<DirectoryEntry> itinerary,
            final DirectoryEntryMap entryMap) {
        // Check conditions
        if (dataType == null || itinerary == null) {
            logger.debug("Cannot operate on null dataType or null itinerary");
            return;
        }

        // Extract the list of interested faces from the map
        final List<ItineraryFace> faceList = this.faces.get(dataType);
        if (faceList == null) {
            logger.debug("Nothing registered for dataType=" + dataType);
            return;
        }

        logger.debug("ItineraryFacet thinking on " + dataType + " begins with itinerary size=" + itinerary.size());

        // Let each one have a look
        for (final ItineraryFace f : faceList) {
            f.process(dataType, payload, itinerary, entryMap);
        }

        logger.debug("ItineraryFacet thinking on " + dataType + " ends with itinerary size=" + itinerary.size());
    }

    /**
     * Grab the itinerary facet of the object
     */
    public static ItineraryFacet of(final IAggregator obj) throws Exception {
        final Facet f = emissary.core.Facet.of(obj, ITINERARY_FACET_NAME);

        if ((f != null) && !(f instanceof ItineraryFacet)) {
            throw new Exception("Wrong facet type: " + f.getClass().getName());
        }
        return (ItineraryFacet) f;
    }

    /**
     * The size of the facet (number of faces)
     */
    public int size() {
        return this.faces.size();
    }
}
