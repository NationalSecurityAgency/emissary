package emissary.kff;

/**
 * Interface for a KFF Implementation to follow
 */
public interface KffFilter {

    /**
     * Types of filter
     */
    enum FilterType {
        UNKNOWN, IGNORE, DUPLICATE
    }

    String getName();

    FilterType getFilterType();

    boolean check(String fname, ChecksumResults sums) throws Exception;

}
