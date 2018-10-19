package emissary.jni;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

public class JniRepositoryPlace extends ServiceProviderPlace {

    protected List<String> libraryDirectoryString = new ArrayList<String>();
    protected List<File> libraryDirectoryFile = new ArrayList<File>();

    /**
     * The remote constructor
     */
    public JniRepositoryPlace(final String cfgInfo, final String dir, final String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();

        logger.info("JniRepository: Prioritized repository list: " + this.libraryDirectoryString);
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
                logger.warn("Invalid libraryDirectory: " + this.libraryDirectoryString.get(i) + " is not a directory.");
                this.libraryDirectoryFile.set(i, null);
            } else if (!(this.libraryDirectoryFile.get(i)).canRead()) {
                logger.warn("Invalid libraryDirectory: " + this.libraryDirectoryString.get(i) + " is not readable.");
                this.libraryDirectoryFile.set(i, null);
            }
        }
    }

    /**
     * Allow other places to ask if we have a certain library available.
     */

    public/* synchronized */boolean nativeLibraryQuery(final String query) {

        // final String finalQuery = new String(query);

        for (int i = 0; i < this.libraryDirectoryFile.size(); i++) {
            final File dir = this.libraryDirectoryFile.get(i);
            if (dir == null) {
                continue;
            }

            final String[] fileList = dir.list(new FilenameFilter() {
                @Override
                public boolean accept(final File dir, final String name) {
                    return name.equals(query);
                }
            });

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
    public/* synchronized */byte[] nativeLibraryDeliver(final String query) throws RemoteException {

        final File nativeLib = nativeLibraryLookup(query);

        if ((nativeLib == null) || !nativeLib.isFile() || !nativeLib.canRead()) {
            logger.warn("NativeLibraryDeliver did not find file " + query);
            return null;
        }

        try {
            final FileInputStream theFile = new FileInputStream(nativeLib);
            final DataInputStream theStream = new DataInputStream(theFile);
            final byte[] theContent = new byte[theStream.available()];
            theStream.readFully(theContent);
            theStream.close();
            theFile.close();
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
    private File nativeLibraryLookup(final String query) {

        String[] fileList = null;
        final String finalQuery = query;
        int theElement = -1;

        for (int i = 0; i < this.libraryDirectoryFile.size(); i++) {
            final File dir = this.libraryDirectoryFile.get(i);

            if (dir == null) {
                continue;
            }

            fileList = dir.list(new FilenameFilter() {
                @Override
                public boolean accept(final File dir, final String name) {
                    return name.equals(finalQuery);
                }
            });

            if (fileList == null || fileList.length == 0) {
                continue;
            }

            theElement = i;
            break;
        }

        if (fileList == null || fileList.length == 0 || theElement == -1) {
            return null;
        }

        final File nativeLib = new File(this.libraryDirectoryFile.get(theElement), fileList[0]);
        return nativeLib;
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
