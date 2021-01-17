package emissary.util.roll;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import emissary.roll.Rollable;
import emissary.util.io.FileNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows for use within the Emissary Rolling framework. Keeps track of bytes written and is thread safe.
 */
public class RollableFileOutputStream extends OutputStream implements Rollable {
    private static final Logger LOG = LoggerFactory.getLogger(RollableFileOutputStream.class);
    /** Locks for protecting writes to underlying stream */
    final ReentrantLock lock = new ReentrantLock();
    /** Flag to let callers know if this class is currently rolling */
    volatile boolean rolling;
    /** Current output stream we're writing to */
    OutputStream fileOutputStream;
    /** Current File we're writing to */
    Path currentFile;
    /** File Name Generator for creating unique file names */
    FileNameGenerator namegen;
    /** Directory we're writing to */
    private Path dir;
    /** Number of bytes written to file */
    long bytesWritten;
    /** Whether to delete a zero byte file */
    boolean deleteZeroByteFiles = true;
    /**
     * internal sequencer in case FileNameGenerator does not obey contract. Present for defense only
     */
    private final AtomicLong seq = new AtomicLong();

    @Deprecated
    public RollableFileOutputStream(FileNameGenerator namegen, File dir) throws IOException {
        this(namegen, dir == null ? null : dir.toPath());
    }

    public RollableFileOutputStream(FileNameGenerator namegen, Path dir) throws IOException {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Directory is invalid: " + dir);
        }
        this.namegen = namegen;
        this.dir = dir;
        handleOrphanedFiles();
        open();
    }

    public RollableFileOutputStream(FileNameGenerator namegen) throws IOException {
        this(namegen, Paths.get("."));
    }

    private void handleOrphanedFiles() {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir, path -> path.getFileName().toString().startsWith("."))) {
            for (Path path : paths) {
                LOG.info("Renaming orphaned file, {}, to non-dot file.", path.getFileName());
                rename(path);
            }
        } catch (IOException ioe) {
            LOG.error("There was an error cleaning orphaned files", ioe);
        }
    }

    private void open() throws IOException {
        Path newFile = getNewFile();
        currentFile = newFile;
        fileOutputStream = Files.newOutputStream(newFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private Path getNewFile() {
        String newName = namegen.nextFileName();
        String dotFile = "." + newName;
        String seqFname = "." + seq.get() + "_" + newName;
        if (currentFile != null && (dotFile.equals(currentFile.getFileName()) || seqFname.equals(currentFile.getFileName()))) {
            LOG.warn("Duplicate file name returned from {}. Using internal sequencer to uniquify.", namegen.getClass());
            dotFile = "." + seq.getAndIncrement() + "_" + newName;
        }
        return Paths.get(dir.toString(), dotFile);
    }

    private void closeAndRename() throws IOException {
        fileOutputStream.flush();
        if (!internalClose(fileOutputStream)) {
            LOG.error("Error closing file {}", currentFile.toAbsolutePath());
        }
        rename(currentFile);
        bytesWritten = 0L;
    }

    private void rename(Path f) throws IOException {
        if (f.toFile().length() == 0L && deleteZeroByteFiles) {
            LOG.debug("Deleting Zero Byte File {}", f.toAbsolutePath());
            Files.deleteIfExists(f);
            return;
        }
        // drop the dot...
        String nonDot = f.getFileName().toString().substring(1);
        Path nd = Paths.get(dir.toString(), nonDot);
        // This shouldn't happen
        if (Files.exists(nd)) {
            LOG.error("Non dot file {} already exists. Forcing unique name.", nd.toAbsolutePath());
            nd = Paths.get(dir.toString(), nonDot + UUID.randomUUID().toString());
        }
        Files.move(f, nd);
        if (!Files.exists(nd)) {
            LOG.error("Rename from {} to {} failed.", f.toAbsolutePath(), nd.toAbsolutePath());
        }
    }

    /**
     * Rolls current file. Exact workflow is closing of the underlying output, renaming the file to it's final name, and
     * opening a new file.
     */
    @Override
    public void roll() {
        lock.lock();
        try {
            rolling = true;
            closeAndRename();

            open();
        } catch (IOException e) {
            LOG.error("Exception during roll of " + currentFile, e);
        } finally {
            rolling = false;
            lock.unlock();
        }
    }

    /**
     * True is this object is in the middle of a roll.
     * 
     * @return true if rolling
     */
    @Override
    public boolean isRolling() {
        return rolling;
    }

    private static boolean internalClose(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Exception e) {
            LOG.warn("Error occurred while closing file", e);
            return false;
        }
        return true;
    }

    /**
     * Closes the underlying outputs and renames the current file to its final name. A new file is NOT opened. Further use
     * of the instance of this class is not guaranteed to function after calling this method.
     */
    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            closeAndRename();
            fileOutputStream = null;
            currentFile = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Thread safe write of a byte
     * 
     * @param b byte to write
     */
    @Override
    public void write(int b) throws IOException {
        lock.lock();
        try {
            fileOutputStream.write(b);
            bytesWritten++;
        } finally {
            lock.unlock();
        }

    }

    /**
     * Thread safe write of byte array.
     * 
     * @param b the data
     * @param off the start offset of the data
     * @param len the number of bytes to write
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        lock.lock();
        try {
            fileOutputStream.write(b, off, len);
            bytesWritten += len;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Number of bytes written to current output file. This value is reset once roll() is called.
     * 
     * @return the number of bytes written
     */
    public long getBytesWritten() {
        return bytesWritten;
    }

    /**
     * Whether zero byte files will be deleted.
     * 
     * @return true is zero byte files will be deleted
     */
    public boolean isDeleteZeroByteFiles() {
        return deleteZeroByteFiles;
    }

    /**
     * Determines whether to delete zero byte files on a roll. If true, both bytes written and the size of the output file
     * are checked. If both are zero, and deleteZeroByteFiles is true, the file is deleted.
     */
    public void setDeleteZeroByteFiles(boolean deleteZeroByteFiles) {
        this.deleteZeroByteFiles = deleteZeroByteFiles;
    }

}
