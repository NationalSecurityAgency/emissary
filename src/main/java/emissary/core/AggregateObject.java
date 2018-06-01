package emissary.core;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An object that can aggregate various facets
 */
public class AggregateObject implements IAggregator {
    // Log
    private static final Logger logger = LoggerFactory.getLogger(AggregateObject.class);

    /** Dynamic facets to the place with their names */
    protected Map<String, Facet> facets;

    /**
     * Create one
     */
    public AggregateObject() {}

    /**
     * Add a facet
     * 
     * @param facet the facet to add
     */
    protected synchronized void addFacet(final Facet facet) {
        if (this.facets == null) {
            this.facets = new HashMap<String, Facet>();
        }
        this.facets.put(facet.getName(), facet);
        logger.debug("Added facet " + facet.getName() + " to " + this.getClass().getName());
    }

    /**
     * Get a facet of the place
     * 
     * @param name the name of the facet to retrieve
     * @return the named Facet or null if no such facet exists
     */
    @Override
    public Facet getFacet(final String name) {
        if (this.facets != null) {
            return this.facets.get(name);
        } else {
            return null;
        }
    }

    /**
     * Remove a facet of the place
     * 
     * @param name the name of the facet to remove
     * @return the facet being removed or null if none found
     */
    protected Facet removeFacet(final String name) {
        if (this.facets != null) {
            return this.facets.remove(name);
        } else {
            return null;
        }
    }
}
