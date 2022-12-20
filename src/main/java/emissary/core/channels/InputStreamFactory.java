package emissary.core.channels;

import java.io.IOException;
import java.io.InputStream;

/**
 * A repeatable way to obtain an InputStream from an object.
 *
 * @param <T> Type of {@link InputStream} instances that the factory can create
 */
public interface InputStreamFactory<T extends InputStream> {
    /**
     * Get an InputStream instance for the factory object
     *
     * @return an InputStream instance
     * @throws IOException if there is a problem creating or retrieving the input stream
     */
    T create() throws IOException;
}
