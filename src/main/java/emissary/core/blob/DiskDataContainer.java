package emissary.core.blob;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PreDestroy;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.roll.RollManager;
import emissary.roll.Rollable;
import emissary.roll.Roller;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disk backed data container, for larger objects.
 *
 */
public class DiskDataContainer implements IDataContainer, Externalizable {

    private static Logger LOG = LoggerFactory.getLogger(DiskDataContainer.class);

    private static byte[] NO_DATA = new byte[0];

    static final String TEMP_FILE_PREFIX = "emissary";

    private static final long serialVersionUID = -3688501921247969743L;

    static String tempFilePath = System.getProperty("java.io.tmpdir");

    private static boolean keepCache = true;

    /** The backing file */
    private transient File file;

    /** In memory cache */
    private transient SoftReference<byte[]> cache = new SoftReference<byte[]>(NO_DATA);

    static {
        try {
            Configurator c = ConfigUtil.getConfigInfo(DiskDataContainer.class);
            keepCache = c.findBooleanEntry("payload.diskContainer.keepCache", true);
            tempFilePath = c.findStringEntry("payload.diskContainer.tempFilePath", System.getProperty("java.io.tmpdir"));
            Pattern subsPattern = Pattern.compile("\\$\\{([^}]+)\\}");
            Matcher subsMatcher = subsPattern.matcher(tempFilePath);
            StringBuffer realPath = new StringBuffer(tempFilePath.length());
            while (subsMatcher.find()) {
                subsMatcher.appendReplacement(realPath, System.getProperty(subsMatcher.group(1)));
            }
            subsMatcher.appendTail(realPath);
            tempFilePath = realPath.toString();
            new File(tempFilePath).mkdirs();
        } catch (IOException e) {
            LOG.warn("Could not load configuration, using defaults.", e);
        }
    }

    /**
     * Create a new instance and immediately register it for garbage collection cleanup.
     */
    public DiskDataContainer() {
        File tmpFileRoot = new File(tempFilePath);
        try {
            this.file = File.createTempFile(TEMP_FILE_PREFIX, ".bdo", tmpFileRoot);
            CLEANUP_TASK.cleanupLater(new GarbageCollectDetector(this));
            ContainerMonitor.getInstance().register(this);
        } catch (IOException ioEx) {
            throw new DataException(ioEx);
        }
    }

    @Override
    public byte[] data() {
        byte[] cached = cache.get();
        if (cached != null) {
            return cached;
        }
        // Read from disk
        @SuppressWarnings("deprecation")
        final byte[] result = new byte[dataLength()];
        int offset = 0;
        final ByteBuffer bb = ByteBuffer.allocate(1024);
        int read;
        try (SeekableByteChannel dataChannel = getFileChannel()) {
            while ((read = dataChannel.read(bb)) != -1) {
                bb.rewind();
                bb.get(result, offset, read);
                offset += read;
                bb.rewind();
            }
            if (keepCache) {
                this.cache = new SoftReference<byte[]>(result);
            }
            return result;
        } catch (IOException ioEx) {
            LOG.error("Could not read data from disk.", ioEx);
            throw new DataException(ioEx);
        }
    }

    @Override
    public void setData(byte[] newData) {
        if (newData == null) {
            setData(NO_DATA);
        } else {
            writeData(newData, 0, newData.length);
            if (keepCache) {
                this.cache = new SoftReference<byte[]>(newData);
            }
        }
    }

    @Override
    public void setData(byte[] newData, int offset, int length) {
        if (newData == null) {
            setData(NO_DATA);
        } else {
            writeData(newData, offset, length);
            if (keepCache) {
                byte[] data = new byte[length];
                System.arraycopy(newData, offset, data, 0, length);
                this.cache = new SoftReference<byte[]>(data);
            }
        }
    }

    private void writeData(byte[] newData, int offset, int length) {
        try (SeekableByteChannel dataChannel = newChannel(newData.length)) {
            dataChannel.write(ByteBuffer.wrap(newData, offset, length));
        } catch (IOException ioEx) {
            LOG.error("Could not write data to disk.", ioEx);
            throw new DataException(ioEx);
        }
    }

    @Override
    public long length() {
        return file.length();
    }

    @Override
    public ByteBuffer dataBuffer() {
        try {
            // Cache _may_ no longer be valid
            invalidateCache();
            return getFileChannel().map(MapMode.READ_WRITE, 0, length());
        } catch (IOException e) {
            LOG.error("Could not map FileChannel to ByteBuffer.", e);
            throw new DataException(e);
        }
    }

    @Override
    public SeekableByteChannel channel() throws IOException {
        FileChannel baseChannel = getFileChannel();
        WrappedSeekableByteChannel<FileChannel> result = new WrappedSeekableByteChannel<>(baseChannel);
        result.setWriteAction(x -> {
            cache.clear();
        });
        return result;
    }

    @Override
    public SeekableByteChannel newChannel(long estimatedSize) throws IOException {
        if (file.exists()) {
            file.delete();
        }
        cache.clear();
        file.createNewFile();
        return channel();
    }

    private FileChannel getFileChannel() throws IOException {
        return FileChannel.open(file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);

    }

    @Override
    public DiskDataContainer clone() throws CloneNotSupportedException {
        DiskDataContainer c = new DiskDataContainer();
        try (InputStream in = Channels.newInputStream(getFileChannel());
                OutputStream out = Channels.newOutputStream(c.newChannel(length()))) {
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            LOG.error("Could not copy data for clone.", e);
            throw new DataException(e);
        }
        return c;
    }

    @Override
    public void invalidateCache() {
        cache.clear();
    }

    @Override
    public IFileProvider getFileProvider() {
        // Assume the client will mutate the data and that the cache is now invalid
        invalidateCache();
        LOG.debug("Client has used direct file access for {}", file.getAbsolutePath());

        return new IFileProvider() {

            @Override
            public void close() throws Exception {}

            @Override
            public File getFile() throws IOException {
                return file;
            }
        };
    }

    /**
     * ReferenceQueue to which the JVM will enqueue {@link GarbageCollectDetector} instances once the relevant
     * {@link DiskDataContainer} is garbage collected.
     */
    static final ReferenceQueue<DiskDataContainer> DELETE_QUEUE = new ReferenceQueue<>();

    /**
     * {@link PhantomReference} to a {@link DiskDataContainer} that maintains a strong reference to the file path such that
     * the file can be identified for deletion once the {@link DiskDataContainer} has been garbage collected.
     *
     * @author adyoun2
     *
     */
    private static final class GarbageCollectDetector extends PhantomReference<DiskDataContainer> {
        /** Path at which the relevant file was stored. */
        private final Path path;

        /**
         * Construct a new instance, and assign its queue on garbage collection to {@link #DELETE_QUEUE}.
         *
         * @param container The container for which to detect garbage collection.
         */
        public GarbageCollectDetector(DiskDataContainer container) {
            super(container, DELETE_QUEUE);
            this.path = container.file.toPath();
        }

        /**
         * Get the path if the file that was used to back the container that has been garbage collected.
         *
         * @return the path.
         */
        public Path getPath() {
            return path;
        }
    }

    /**
     * Background task to detect when the JVM has enqueued {@link GarbageCollectDetector}s for containers that have been
     * garbage collected.
     *
     * @author adyoun2
     *
     */
    private static final class DiskCleanupTask implements Runnable {
        /**
         * Set of strong references to {@link GarbageCollectDetector}s, such that the JVM cannot garbage collect the reference
         * before the target is enqueued.
         */
        private final Set<GarbageCollectDetector> activePersistence = new HashSet<>();

        public DiskCleanupTask() {}

        @Override
        public void run() {
            /**
             * Keep alive for the cleanup thread, and allow hook into last stage of Emissary shutdown.
             */
            RollManager.getManager().addRoller(new Roller(TimeUnit.SECONDS, 10, new Rollable() {

                @Override
                public void close() throws IOException {
                    LOG.info("System shutdown detected, deleting remaining files.");
                    shutDown();
                }

                @Override
                public void roll() {
                    if (!CLEANUP_THREAD.isAlive()) {
                        CLEANUP_THREAD.start();
                    }
                }

                @Override
                public boolean isRolling() {
                    return true;
                }
            }));

            while (true) {
                final GarbageCollectDetector reference = (GarbageCollectDetector) DELETE_QUEUE.poll();
                if (reference != null) {
                    try {
                        activePersistence.remove(reference);
                        File f = reference.getPath().toFile();
                        if (f.exists()) {
                            LOG.trace("Garbage collection detected, deleting file {}", f.getAbsolutePath());
                            f.delete();
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to remove {} on garbage collection.", reference.getPath(), e);
                    }
                } else {
                    try {
                        LOG.trace("No DiskDataContainers have been garbage collected.");
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
                    File f = reference.getPath().toFile();
                    if (f.exists()) {
                        LOG.trace("JVM shutdown detected, deleting file {}", f.getAbsolutePath());
                        f.delete();
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to remove {} on JVM shutdown.", reference.getPath(), e);
                }
                iter.remove();
            }
        }
    }

    /** Singleton instance of the cleanup task */
    private static final DiskCleanupTask CLEANUP_TASK = new DiskCleanupTask();
    /** The Thread the cleanup task is running in. */
    private static final Thread CLEANUP_THREAD = new Thread(CLEANUP_TASK);
    static {
        CLEANUP_THREAD.setName("Emissary DiskDataContainer cleanup");
        CLEANUP_THREAD.setDaemon(true);
        CLEANUP_THREAD.setPriority(Thread.MIN_PRIORITY);
        CLEANUP_THREAD.start();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        byte[] cached = cache.get();
        if (cached != null) {
            out.write(cached);
        } else {
            try (SeekableByteChannel channel = getFileChannel()) {
                IOUtils.copyLarge(Channels.newInputStream(channel), (OutputStream) out);
            }
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try (SeekableByteChannel channel = newChannel(1000)) {
            IOUtils.copyLarge((InputStream) in, Channels.newOutputStream(channel));
        }
    }
}
