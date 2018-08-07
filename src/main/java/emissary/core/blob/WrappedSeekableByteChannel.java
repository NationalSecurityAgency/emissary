package emissary.core.blob;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * {@link SeekableByteChannel} that proxies calls to a wrapped instance, but where listeners for mutation and closure
 * may be registered.
 *
 * @author adyoun2
 *
 * @param <T> The type of the wrapped instance.
 */
public class WrappedSeekableByteChannel<T extends SeekableByteChannel> implements SeekableByteChannel {

    /** The wrapped instance. */
    private T baseChannel;
    /** Listener for the channel being closed. */
    private TriggeredAction<T> closeAction;
    /** Listener for the channel being mutated. */
    private TriggeredAction<T> writeAction;

    /**
     * Get the listener on channel close.
     *
     * @return the listener on channel close.
     */
    public TriggeredAction<T> getCloseAction() {
        return closeAction;
    }

    /**
     * Set a listener for channel close. Only one listener may be registered, and exceptions from the listener will not
     * be handled cleanly.
     *
     * @param closeAction the new close listener.
     */
    public void setCloseAction(TriggeredAction<T> closeAction) {
        this.closeAction = closeAction;
    }

    /**
     * Get the listener on channel write/truncate.
     *
     * @return the listener on channel write/truncate.
     */
    public TriggeredAction<T> getWriteAction() {
        return writeAction;
    }

    /**
     * Set a listener for channel data mutation. Only one listener may be registered, and exceptions from the listener
     * will not be handled cleanly.
     *
     * @param writeAction the new write/truncate listener.
     */
    public void setWriteAction(TriggeredAction<T> writeAction) {
        this.writeAction = writeAction;
    }

    /**
     * New instance.
     *
     * @param baseChannel the channel to wrap.
     */
    public WrappedSeekableByteChannel(T baseChannel) {
        this.baseChannel = baseChannel;
    }

    /**
     * Replace the wrapped channel. No cleanup of the original channel is performed.
     *
     * @param replacement the channel to wrap.
     */
    void replaceBaseChannel(T replacement) {
        this.baseChannel = replacement;
    }

    @Override
    public boolean isOpen() {
        return baseChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        baseChannel.close();
        if (this.closeAction != null) {
            closeAction.onTrigger(baseChannel);
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return baseChannel.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int result = baseChannel.write(src);
        if (writeAction != null) {
            writeAction.onTrigger(baseChannel);
        }
        return result;
    }

    @Override
    public long position() throws IOException {
        return baseChannel.position();
    }

    @SuppressWarnings("unchecked")
    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        this.baseChannel = (T) baseChannel.position(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return baseChannel.size();
    }

    @SuppressWarnings("unchecked")
    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        this.baseChannel = (T) baseChannel.truncate(size);
        if (writeAction != null) {
            writeAction.onTrigger(baseChannel);
        }
        return this;
    }

    /**
     * Notification that the action this was registered against has occurred.
     *
     * @author adyoun2
     *
     * @param <T>
     */
    @FunctionalInterface
    public static interface TriggeredAction<T extends SeekableByteChannel> {
        /**
         * Notification that the action this was registered against has occurred.
         *
         * @param baseChannel The implementing channel the operation occurred on.
         * @throws IOException handling of thrown exceptions is undefined.
         */
        void onTrigger(T baseChannel) throws IOException;
    }
}
