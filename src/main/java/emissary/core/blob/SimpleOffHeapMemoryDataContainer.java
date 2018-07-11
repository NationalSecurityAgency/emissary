package emissary.core.blob;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PreDestroy;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Data container using off heap memory allocation.
 *
 * @author adyoun2
 *
 */
public class SimpleOffHeapMemoryDataContainer implements IDataContainer, Externalizable {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleOffHeapMemoryDataContainer.class);

    private static final byte[] NO_DATA = new byte[0];

    /**
     * The pointer value of the start of the memory. A final mutable object such that the
     * {@link GarbageCollectDetector}'s reference to the pointer is always correct.
     */
    private transient final AtomicLong memoryHandle = new AtomicLong(0L);
    /**
     * The amount of memory actually allocated. May be larger that the current data size;
     */
    private transient final AtomicLong allocatedBytes = new AtomicLong(0L);
    /**
     * The number of bytes actually used.
     */
    private transient long length = 0;

    /**
     * Create a new instance and immediately register it for garbage collection cleanup.
     */
    public SimpleOffHeapMemoryDataContainer() {
        CLEANUP_TASK.cleanupLater(new GarbageCollectDetector(this));
        ContainerMonitor.getInstance().register(this);
    }

    @Override
    public byte[] data() {
        if (memoryHandle.get() == 0L) {
            return NO_DATA;
        }
        return new Pointer(memoryHandle.get()).getByteArray(0, (int) length);
    }

    @Override
    public void setData(byte[] newData) {
        if (newData == null || newData.length == 0) {
            if (memoryHandle.get() != 0L) {
                Native.free(memoryHandle.get());
                memoryHandle.set(0L);
                allocatedBytes.set(0L);
            }
            return;
        }
        setData(newData, 0, newData.length);
    }

    @Override
    public void setData(byte[] newData, int offset, int length) {
        if (memoryHandle.get() != 0L) {
            Native.free(memoryHandle.get());
        }
        if (newData == null || length == 0) {
            memoryHandle.set(0L);
            allocatedBytes.set(0L);
            return;
        }
        this.length = length;
        memoryHandle.set(Native.malloc(this.length));
        allocatedBytes.set(this.length);
        new Pointer(memoryHandle.get()).write(0, newData, offset, (int) this.length);
    }

    @Override
    public ByteBuffer dataBuffer() {
        if (memoryHandle.get() == 0L) {
            return ByteBuffer.wrap(NO_DATA);
        }
        return new Pointer(memoryHandle.get()).getByteBuffer(0, length);
    }

    @Override
    public SimpleOffHeapMemoryDataContainer clone() throws CloneNotSupportedException {
        SimpleOffHeapMemoryDataContainer clone = (SimpleOffHeapMemoryDataContainer) super.clone();
        try {
			IOUtils.copyLarge(Channels.newInputStream(channel()), Channels.newOutputStream(clone.newChannel(length())));
		} catch (IOException e) {
			throw new DataException(e);
		}
        return clone;
    }

    @Override
    public SeekableByteChannel channel() throws IOException {
        return new OffHeapChannel();
    }

    @Override
    public SeekableByteChannel newChannel(long estimatedSize) throws IOException {
        if (memoryHandle.get() != 0L) {
            Native.free(memoryHandle.get());
        }
        long sizeToAllocate = (long) (estimatedSize * 1.2);
        long newHandle = Native.malloc(sizeToAllocate);
        allocatedBytes.set(sizeToAllocate);
        memoryHandle.set(newHandle);
        length = 0;
        OffHeapChannel result = new OffHeapChannel();
        return result;
    }

    /**
     * Channel that reads and writes to the off heap memory.
     *
     * @author adyoun2
     *
     */
    private class OffHeapChannel implements SeekableByteChannel {

        private boolean open = true;
        /** The current read/write position. */
        private long position = 0L;

        @Override
        public boolean isOpen() {
            return this.open;
        }

        @Override
        public void close() throws IOException {
            open = false;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            Pointer pointer = new Pointer(memoryHandle.get());
            // The volume of data we want to/can read
            int sizeToRead = dst.remaining();
            long remaining = length - position;
            if (remaining < sizeToRead) {
                sizeToRead = (int) remaining;
            }
            if (sizeToRead <= 0) {
                return -1;
            }
            LOG.trace("Reading {} bytes from off heap memory.", sizeToRead);
            byte[] b = pointer.getByteArray(position, sizeToRead);
            dst.put(b, 0, b.length);
            position += b.length;
            return b.length;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            int bytesToWrite = src.remaining();
            long oldAllocation = allocatedBytes.get();

            if (position + bytesToWrite > oldAllocation) {
                LOG.debug("Write past current memory allocation, re-allocate the memory and copy the data, old allocation {}", oldAllocation);
                long newSize = oldAllocation < 100 ? oldAllocation + bytesToWrite : oldAllocation * 2;
                while (position + bytesToWrite > newSize) {
                    newSize *= 2;
                }
                LOG.debug("Re-allocating to {} bytes", newSize);
                long newHandle = Native.malloc(newSize);
                Pointer newData = new Pointer(newHandle);

                long oldHandle = memoryHandle.get();
                if (oldHandle != 0) {
                    LOG.debug("There was old data, copy it and release the old data.");
                    Pointer oldData = new Pointer(oldHandle);
                    byte[] buf = new byte[1024];
                    for (int i = 0; i < oldAllocation; i += 1024) {
                        int len = (int) (i < oldAllocation - 1024 ? 1024 : oldAllocation - i);
                        oldData.read(i, buf, 0, len);
                        newData.write(i, buf, 0, len);
                    }
                    Native.free(oldHandle);
                }

                allocatedBytes.set(newSize);
                memoryHandle.set(newHandle);
            }

            // Write the data.
            Pointer pointer = new Pointer(memoryHandle.get());
            if (src.hasArray()) {
                pointer.write(position, src.array(), src.position(), bytesToWrite);
                src.position(src.position() + bytesToWrite);
            } else {
                byte[] buf = new byte[src.remaining()];
                src.get(buf);
                pointer.write(position, buf, 0, buf.length);
            }
            LOG.trace("Written {} bytes to off heap memory.", bytesToWrite);
            position += bytesToWrite;
            if (length < position) {
                length = position;
            }
            return bytesToWrite;
        }

        @Override
        public long position() throws IOException {
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            this.position = newPosition;
            return this;
        }

        @Override
        public long size() throws IOException {
            return length;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            // Inefficiently just ignore later bytes, but don't shrink the allocation
            length = Long.min(length, size);
            if (length == size) {
                position = Long.min(length, position);
            }
            return this;
        }

    }

    @Override
    public long length() {
        return length;
    }

    /**
     * ReferenceQueue to which the JVM will enqueue {@link GarbageCollectDetector} instances once the relevant
     * {@link SimpleOffHeapMemoryDataContainer} is garbage collected.
     */
    static final ReferenceQueue<SimpleOffHeapMemoryDataContainer> DELETE_QUEUE = new ReferenceQueue<>();

    /**
     * {@link PhantomReference} to a {@link SimpleOffHeapMemoryDataContainer} that maintains a strong reference to the
     * file path such that the file can be identified for deletion once the {@link SimpleOffHeapMemoryDataContainer} has
     * been garbage collected.
     *
     * @author adyoun2
     *
     */
    private static final class GarbageCollectDetector extends PhantomReference<SimpleOffHeapMemoryDataContainer> {
        /** A pointer to the memory that will need freeing. */
        private final AtomicLong handle;

        /**
         * Construct a new instance, and assign its queue on garbage collection to {@link #DELETE_QUEUE}.
         *
         * @param container The container for which to detect garbage collection.
         */
        public GarbageCollectDetector(SimpleOffHeapMemoryDataContainer container) {
            super(container, DELETE_QUEUE);
            this.handle = container.memoryHandle;
        }

        /**
         * Get a pointer to the memory that was used to back the container that has been garbage collected.
         *
         * @return the pointer.
         */
        public AtomicLong getHandle() {
            return handle;
        }
    }

    /**
     * Background task to detect when the JVM has enqueued {@link GarbageCollectDetector}s for containers that have been
     * garbage collected.
     *
     * @author adyoun2
     *
     */
    private static final class OffHeapMemoryCleanupTask implements Runnable {
        /**
         * Set of strong references to {@link GarbageCollectDetector}s, such that the JVM cannot garbage collect the
         * reference before the target is enqueued.
         */
        private final Set<GarbageCollectDetector> activePersistence = new HashSet<>();

        public OffHeapMemoryCleanupTask() {}

        @Override
        public void run() {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    shutDown();
                }
            });
            while (true) {
                final GarbageCollectDetector reference = (GarbageCollectDetector) DELETE_QUEUE.poll();
                if (reference != null) {
                    try {
                        activePersistence.remove(reference);
                        AtomicLong handle = reference.getHandle();
                        if (handle.get() != 0) {
                            LOG.trace("Garbage collection detected, freeing memory {}", handle);
                            Native.free(handle.get());
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to free {} on garbage collection.", reference.getHandle(), e);
                    }
                } else {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        /**
         * Register a {@link GarbageCollectDetector} as requiring post garbage collection cleanup.
         *
         * @param ref the reference to register.
         */
        public void cleanupLater(GarbageCollectDetector ref) {
            activePersistence.add(ref);
        }

        /**
         * Attempt to cleanup any outstanding resource at app shutdown. Not guaranteed to activate.
         */
        @PreDestroy
        public synchronized void shutDown() {
            final Iterator<GarbageCollectDetector> iter = activePersistence.iterator();
            while (iter.hasNext()) {
                final GarbageCollectDetector reference = iter.next();
                try {
                    AtomicLong handle = reference.getHandle();
                    if (handle.get() != 0) {
                        LOG.trace("JVM shutdown detected, freeing memory {}", handle);
                        Native.free(handle.get());
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to free {} on JVM shutdown.", reference.getHandle(), e);
                }
                iter.remove();
            }
        }
    }

    /** Singleton instance of the cleanup task */
    private static final OffHeapMemoryCleanupTask CLEANUP_TASK = new OffHeapMemoryCleanupTask();
    /** The Thread the cleanup task is running in. */
    private static final Thread CLEANUP_THREAD = new Thread(CLEANUP_TASK);
    static {
        CLEANUP_THREAD.setName("Emissary SimpleOffHeapMemory cleanup");
        CLEANUP_THREAD.setDaemon(true);
        CLEANUP_THREAD.setPriority(Thread.MIN_PRIORITY);
        CLEANUP_THREAD.start();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        try (InputStream channel = Channels.newInputStream(channel())) {
            IOUtils.copyLarge(channel, (OutputStream) out);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try (SeekableByteChannel channel = newChannel(in.available())) {
            IOUtils.copyLarge((InputStream) in, Channels.newOutputStream(channel));
        }
    }
}
