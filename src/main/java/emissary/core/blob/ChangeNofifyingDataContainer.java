package emissary.core.blob;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * A decorator for an {@link IDataContainer}, allowing a change listener to be registered.
 *
 */
public class ChangeNofifyingDataContainer implements IDataContainer {

    /**
     * 
     */
    private static final long serialVersionUID = 4466141643920488464L;

    /** Wrapped data container */
    private IDataContainer dc;
    /** Listener */
    private TriggeredAction<IDataContainer> updateAction;

    /**
     * Wrap an {@link IDataContainer} with a notification Facade.
     * 
     * @param dc the data container to be wrapped.
     */
    public ChangeNofifyingDataContainer(IDataContainer dc) {
        this.dc = dc;
    }

    @Override
    public byte[] data() {
        return dc.data();
    }

    @Override
    public void setData(byte[] newData) {
        dc.setData(newData);
        updateAction.onTrigger(this);
    }

    @Override
    public void setData(byte[] newData, int offset, int length) {
        dc.setData(newData, offset, length);
        updateAction.onTrigger(this);
    }

    @Override
    public ByteBuffer dataBuffer() {
        return dc.dataBuffer();
    }

    @Override
    public IDataContainer clone() throws CloneNotSupportedException {
        IDataContainer wrapped = dc.clone();
        ChangeNofifyingDataContainer clone = new ChangeNofifyingDataContainer(wrapped);
        clone.updateAction = updateAction; // should this be cloned
        return clone;
    }

    @Override
    public SeekableByteChannel channel() throws IOException {
        WrappedSeekableByteChannel<SeekableByteChannel> sbc = new WrappedSeekableByteChannel<>(dc.channel());
        sbc.setWriteAction((dc) -> sbc.setCloseAction(x -> updateAction.onTrigger(ChangeNofifyingDataContainer.this)));
        return sbc;
    }

    @Override
    public SeekableByteChannel newChannel(long estimatedSize) throws IOException {
        WrappedSeekableByteChannel<SeekableByteChannel> sbc = new WrappedSeekableByteChannel<>(dc.newChannel(estimatedSize));
        sbc.setCloseAction(x -> updateAction.onTrigger(ChangeNofifyingDataContainer.this));
        return sbc;
    }

    @Override
    public long length() {
        return dc.length();
    }

    @Override
    public IFileProvider getFileProvider() {
        return new TempFileProvider(this);
    }

    /**
     * Notification that the action this was registered against has occurred.
     *
     * @param <T>
     */
    @FunctionalInterface
    public static interface TriggeredAction<T extends IDataContainer> {
        /**
         * Notification that the action this was registered against has occurred.
         *
         * @param
         * @throws IOException handling of thrown exceptions is undefined.
         */
        void onTrigger(T changedData);
    }

    /**
     * Register a change listener. Only one listener can be registered.
     * 
     * @param listener the listener.
     */
    public void registerChangeListener(TriggeredAction<IDataContainer> listener) {
        this.updateAction = listener;
    }

}
