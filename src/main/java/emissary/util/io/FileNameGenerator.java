package emissary.util.io;

/**
 * Interface to generate unique filenames to be used in classes as needed.
 */
public interface FileNameGenerator {
    /**
     * Returns the next unique filename from this generator.
     */
    String nextFileName();
}
