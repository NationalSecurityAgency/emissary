package emissary.output.roller;

import java.io.IOException;

import emissary.output.roller.journal.KeyedOutput;
import emissary.roll.Rollable;

/**
 * A {@link Rollable} implementation that uses a journal to record offsets of completed writes to a pool of outputs. The
 * Journal serves as a write ahead log and records positions of all open file handles until rolled.
 */
public interface IJournaler extends Rollable {

    /**
     * Returns and KeyedOutput object containing the final output file and can be written to as either an OutputStream or a
     * SeekableByteChannel. This method will block if objects from the pool have been exhausted.
     *
     * @return a KeyedOutput object
     * @throws IOException If there is some I/O problem.
     */
    KeyedOutput getOutput() throws IOException;
}
