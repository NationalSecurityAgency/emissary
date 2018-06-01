package emissary.core;

/**
 * A base class for facets of a ServiceProviderPlace and a static implementation of facet retrieval
 */
public class Facet {

    public String FACET_NAME = "base-facet";


    /**
     * Plain constructor
     */
    public Facet() {}

    /**
     * Construct facet with name
     */
    public Facet(final String name) {
        this.FACET_NAME = name;
    }

    /**
     * Get the facet name
     */
    public String getName() {
        return this.FACET_NAME;
    }

    /**
     * Set the facet name
     */
    protected void setName(final String s) {
        this.FACET_NAME = s;
    }

    /**
     * Get the facet of an object if it has one
     */
    public static Facet of(final IAggregator obj, final String name) {
        return obj.getFacet(name);
    }

}
