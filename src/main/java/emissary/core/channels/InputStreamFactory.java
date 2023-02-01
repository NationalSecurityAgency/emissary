package emissary.core.channels;

import java.io.IOException;
import java.io.InputStream;

/**
 * A repeatable way to obtain an InputStream from an object.
 */
public interface InputStreamFactory {
    /**
     * Get an InputStream instance for the factory object
     *
     * @return an InputStream instance
     */
    InputStream create() throws IOException;
}
