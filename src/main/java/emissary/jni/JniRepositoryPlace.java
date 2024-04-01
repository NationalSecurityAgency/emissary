package emissary.jni;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class JniRepositoryPlace extends ServiceProviderPlace {

    protected List<String> libraryDirectoryString = new ArrayList<>();
    protected List<File> libraryDirectoryFile = new ArrayList<>();

    /**
     * The remote constructor
     */
    public JniRepositoryPlace(final String cfgInfo, final String dir, final String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();

        logger.info("JniRepository: Prioritized repository list: {}", this.libraryDirectoryString);
    }

    /**
     * The static standalone (test) constructor
     */
    public JniRepositoryPlace(final String cfgInfo) throws IOException {
        super(cfgInfo, "TestJniRepositoryPlace.host.domain.com:8003");
        configurePlace();
    }

    /**
     * Set up place specific information from the config file, load JNI, etc.
     */
    private void configurePlace() {

        // Set libraryDirectory from JniRepositoryPlace.cfg
        this.libraryDirectoryString = configG.findEntries("LIBRARY_DIRECTORY");
        if (this.libraryDirectoryString.isEmpty()) {
            System.err.println("JniRepository: No LIBRARY_DIRECTORY indicators.");
        }

        for (int i = 0; i < this.libraryDirectoryString.size(); i++) {
            this.libraryDirectoryFile.add(new File(this.libraryDirectoryString.get(i)));

            if (!(this.libraryDirectoryFile.get(i)).isDirectory()) {
                logger.warn("Invalid libraryDirectory: {} is not a directory.", this.libraryDirectoryString.get(i));
                this.libraryDirectoryFile.set(i, null);
            } else if (!(this.libraryDirectoryFile.get(i)).canRead()) {
                logger.warn("Invalid libraryDirectory: {} is not readable.", this.libraryDirectoryString.get(i));
                this.libraryDirectoryFile.set(i, null);
            }
        }
    }

    /**
     * Allow other places to ask if we have a certain library available.
     */

    public/* synchronized */boolean nativeLibraryQuery(final String query) {

        for (final File dir : this.libraryDirectoryFile) {
            if (dir == null) {
                continue;
            }

            final String[] fileList = dir.list((dir1, name) -> name.equals(query));

            if ((fileList != null) && (fileList.length > 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The query string is the name of a native library to serve back. The requesting place is searching for a native
     * library (.so or .dll) to use. If we have what the requestor is looking for in our libraryDirectory, serve it back to
     * them as an array of bytes.
     */
    @Nullable
    public /* synchronized */ byte[] nativeLibraryDeliver(final String query) throws RemoteException {

        final File nativeLib = nativeLibraryLookup(query);

        if ((nativeLib == null) || !nativeLib.isFile() || !nativeLib.canRead()) {
            logger.warn("NativeLibraryDeliver did not find file {}", query);
            return null;
        }

        try (final InputStream theFile = Files.newInputStream(nativeLib.toPath());
                final DataInputStream theStream = new DataInputStream(theFile)) {
            final byte[] theContent = new byte[theStream.available()];
            theStream.readFully(theContent);
            return theContent;
        } catch (Exception e) {
            throw new RemoteException("Repository failure", e);
        }
    }

    /**
     * Provide the last modified timestamp for a remote file
     */
    public/* synchronized */long lastModified(final String query) {

        final File theFile = nativeLibraryLookup(query);

        if (theFile == null) {
            return 0L;
        }
        return theFile.lastModified();
    }

    /**
     * Lookup the requested file in the repository either for delivery or timestamp checking
     */
    @Nullable
    private File nativeLibraryLookup(final String query) {

        String[] fileList = null;
        int theElement = -1;

        for (int i = 0; i < this.libraryDirectoryFile.size(); i++) {
            final File dir = this.libraryDirectoryFile.get(i);

            if (dir == null) {
                continue;
            }

            fileList = dir.list((dir1, name) -> name.equals(query));

            if (fileList == null || fileList.length == 0) {
                continue;
            }

            theElement = i;
            break;
        }

        if (fileList == null || fileList.length == 0 || theElement == -1) {
            return null;
        }

        return new File(this.libraryDirectoryFile.get(theElement), fileList[0]);
    }

    /**
     * Satisfy the needs of ServiceProviderPlace even though we do not expect any agents to visit here right now.
     */
    @Override
    public void process(final IBaseDataObject theDataObject) {}

    /**
     * Test standalone main
     */

    public static void main(final String[] argv) {

        if (argv.length < 1) {
            System.err.println("usage: java JniRepositoryPlace file.cfg query ...");
            return;
        }

        final JniRepositoryPlace jnirepositoryplace;
        try {
            jnirepositoryplace = new JniRepositoryPlace(argv[0]);
        } catch (IOException e) {
            System.err.println("Cannot make JniRepositoryPlace: " + e);
            return;
        }

        for (int i = 1; i < argv.length; i++) {
            System.err.println(argv[i] + ":" + (jnirepositoryplace.nativeLibraryQuery(argv[i]) ? "" : " not") + " found");
        }
    }
}
