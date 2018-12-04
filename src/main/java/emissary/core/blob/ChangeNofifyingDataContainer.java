package emissary.core.blob;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public class ChangeNofifyingDataContainer implements IDataContainer {

    /**
     * 
     */
    private static final long serialVersionUID = 4466141643920488464L;

    private IDataContainer dc;
    private TriggeredAction<IDataContainer> updateAction;

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
        // TODO : what to do with listeners?
        return clone;
    }

    @Override
    public SeekableByteChannel channel() throws IOException {
        WrappedSeekableByteChannel<SeekableByteChannel> sbc = new WrappedSeekableByteChannel<>(dc.channel());
        sbc.setCloseAction(x -> updateAction.onTrigger(ChangeNofifyingDataContainer.this));
        return sbc;
    }

    @Override
    public SeekableByteChannel newChannel(long estimatedSize) throws IOException {
        WrappedSeekableByteChannel<SeekableByteChannel> sbc = new WrappedSeekableByteChannel<>(dc.channel());
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

    public void registerChangeListener(TriggeredAction<IDataContainer> listener) {
        this.updateAction = listener;
    }

}
