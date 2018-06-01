package emissary.core;

/**
 * Interface for objects that can aggregate behavioral facets
 */
public interface IAggregator {
    Facet getFacet(String name);
}
