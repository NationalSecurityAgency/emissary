package emissary.kff;

/**
 * Interface for a KFF Implementation to follow
 */
public interface KffFilter {

    /**
     * Types of filter
     */
    public static enum FilterType {
        Unknown, Ignore, Duplicate
    }

    String getName();

    FilterType getFilterType();

    boolean check(String fname, ChecksumResults sums) throws Exception;

}
