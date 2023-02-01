package emissary.core.channels;

import java.nio.channels.SeekableByteChannel;

/**
 * Interface to provide a consistent experience dealing with streaming data.
 */
public interface SeekableByteChannelFactory {

    /**
     * Creates a channel from the referenced data that already exists as part of the factory's parent.
     *
     * See {@link InMemoryChannelFactory} for an example of how to implement this.
     *
     * @return a channel with access to the data
     */
    SeekableByteChannel create();
}
