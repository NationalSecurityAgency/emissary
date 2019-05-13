package emissary.output.roller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * All Journals are identified and their outputs are combined into a destination filename.
 *
 */
public interface ICoalescer {

    /**
     * Combine files into a single output file
     *
     * @throws IOException If there is some I/O problem.
     */
    void coalesce() throws IOException;

    /**
     * @see ICoalescer#coalesce()
     * @param journals the paths to the journal files
     * @throws IOException If there is some I/O problem.
     */
    void coalesce(Collection<Path> journals) throws IOException;
}
