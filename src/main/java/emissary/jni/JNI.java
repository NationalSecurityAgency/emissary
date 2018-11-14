package emissary.jni;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide methods for retrieving native libraries from the repository The main entry point is loadLibrary. Places
 * wishing to loadLibraries from the JniRepositoryPlace should include this class via composition and invoke
 * this.loadLibrary rather than System.loadLibrary()
 */
public class JNI implements Serializable {

    static final long serialVersionUID = 3037911106823343480L;

    /**
     * A handle to the DirectoryPlace
     */
    private transient IDirectoryPlace theDir;

    /**
     * The mappings for SharedPrefix on native libraries
     */
    private final Map<String, String> sharedPrefix = new HashMap<String, String>();

    /**
     * The mappings for SharedSuffixes on native libraries
     */
    private final Map<String, String> sharedSuffix = new HashMap<String, String>();

    /**
     * The mappings for the correct version of each native library
     */
    private final Map<String, String> libVersions = new HashMap<String, String>();

    /**
     * The location we all agree to save library files retrieved from the repository osname-dependently
     */
    private final Map<String, String> savePath = new HashMap<String, String>();

    /**
     * Handle to the configG gives access to the config file entries
     */
    private Configurator configG;

    protected static final Logger logger = LoggerFactory.getLogger(JNI.class);

    /**
     * Public constructor when used as a utility class
     */
    public JNI() throws IOException {
        this.configG = ConfigUtil.getConfigInfo(JNI.class);
        configurePlace();
    }

    /**
     * Public constructor args are easy when called from ServiceProviderPlace
     */
    public JNI(final String theDir, final Configurator configG) {
        if (theDir != null) {
            try {
                this.theDir = (IDirectoryPlace) Namespace.lookup(theDir);
            } catch (NamespaceException ne) {
                logger.debug("Cannot get directory using " + theDir + ": " + ne);
            }
        }

        if (this.theDir == null) {
            try {
                this.theDir = DirectoryPlace.lookup();
            } catch (EmissaryException ex) {
                logger.debug("Unable to lookup default directory", ex);
            }
        }

        this.configG = configG;
        configurePlace();
    }

    /**
     * Configure the place based on the current {@link #configG} setting.
     */
    private void configurePlace() {

        if (this.configG == null) {
            return;
        }

        List<String> parms = this.configG.findEntries("SHARED_PREFIX");
        for (final String entry : parms) {
            final int ndx = entry.indexOf(':');
            if (ndx == -1) {
                logger.warn("Invalid SHARED_PREFIX: " + entry);
                continue;
            }

            final String arch = entry.substring(0, ndx);
            String prefix = "";
            if (ndx < entry.length() - 1) {
                prefix = entry.substring(ndx + 1);
            }

            this.sharedPrefix.put(arch, prefix);

            // Get the osname-dependent SAVE_PATH
            final List<String> iparms = this.configG.findEntries(arch + "_LIBRARY_SAVE_PATH");
            if (iparms.size() > 0) {
                this.savePath.put(arch, iparms.get(0));
            }
        }

        parms = this.configG.findEntries("SHARED_SUFFIX");
        for (final String entry : parms) {
            final int ndx = entry.indexOf(':');
            if (ndx == -1) {
                logger.warn("Invalid SHARED_SUFFIX: " + entry);
                continue;
            }

            final String arch = entry.substring(0, ndx);
            String suffix = "";
            if (ndx < entry.length() - 1) {
                suffix = entry.substring(ndx + 1);
            }
            this.sharedSuffix.put(arch, suffix);
        }

        parms = this.configG.findEntries("LIBRARY_VERSION");
        for (final String entry : parms) {
            final int ndx = entry.indexOf(':');
            if (ndx == -1) {
                logger.warn("Invalid LIBRARY_VERSION: " + entry);
                continue;
            }

            final String lib = entry.substring(0, ndx);
            String ver = "";
            if (ndx < entry.length() - 1) {
                ver = entry.substring(ndx + 1);
            }
            this.libVersions.put(lib, ver);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("JNI config save paths " + this.savePath);
            logger.debug("JNI config prefixes " + this.sharedPrefix);
            logger.debug("JNI config suffixes " + this.sharedSuffix);
            logger.debug("JNI config versions " + this.libVersions);
        }
    }

    /**
     * Expand the root name of the library by the architecture and version constants.
     */
    private String expandLibraryName(final String name) {
        final String osarch = System.getProperty("os.arch").replace(' ', '_');
        final String osname = System.getProperty("os.name").replace(' ', '_');
        // String osver = System.getProperty("os.version").replace(' ','_');
        String libver = this.libVersions.get(name);
        final String sep = "-";

        if (libver == null) {
            libver = "1.0";
        }

        return name + sep + // foo-
                osname + sep + // Solaris-
                osarch + sep + // sparc-
                // osver+sep+ // 2.x-
                libver; // v3.2
    }

    /**
     * Expand the full library name with the way it is stored in the file system. This method expects something like
     * foo-Solaris-sparc-2.x-v3.2 and returns libfoo-Solaris-sparc-2.x-v3.2.so
     */
    private String filesystemLibraryName(final String name) {
        final String osname = System.getProperty("os.name").replace(' ', '_');
        String prefix = this.sharedPrefix.get(osname);
        String suffix = this.sharedSuffix.get(osname);

        if (prefix == null) {
            prefix = "";
        }

        if (suffix == null) {
            suffix = "";
        }

        return prefix + name + suffix;
    }

    /**
     * Load a native library, finding it if it isn't already here. First try to load the library and catch any exception.
     * Try to ask the directory place where the JniRepositoryPlace is and see if the repository has the library we are
     * looking for. It it comes back, save it to disk in the agreed upon location and then load it in the regular way. Throw
     * an UnsatisfiedLinkError if this doesn't work.
     *
     * Note that System.loadLibrary expects the name as it comes back from expandLibraryName while the repository will
     * respond to the name as it comes from filesystemLibraryName.
     *
     * @throws UnsatisfiedLinkError If it fails to load.
     */
    public void loadLibrary(final String lib) {

        final String libname = expandLibraryName(lib);
        final String filename = filesystemLibraryName(libname);
        final String osname = System.getProperty("os.name").replace(' ', '_');
        final String theLocation = this.savePath.get(osname);
        final String fullPathName = theLocation + File.separator + filename;

        logger.debug("In JNI.loadLibrary(" + lib + ")");
        logger.debug("loading library: " + libname);

        // Try it on the LD_LIBRARY_PATH or equivalent
        try {
            System.loadLibrary(libname);
            return;
        } catch (UnsatisfiedLinkError e) {
            final String syspath = System.getProperty("java.library.path", "<none>");
            logger.debug("Unable to link local " + libname + " from incoming " + lib + " using system path " + syspath, e);
        }


        // Try it in the save area, in case it's not on the LD_LIBRARY_PATH
        try {
            System.load(fullPathName);
            return;
        } catch (UnsatisfiedLinkError e) {
            logger.debug("Unable to link abs path " + fullPathName + " from incoming " + lib, e);
        }

        // Retrieve the File and dependencies.
        // errorMsg valid only when return is false
        final String[] errorMsg = new String[1];

        // Retrieve everything listed as a dependency of the requested library.
        // If the library was obtained via System.loadLibrary() or System.load()
        // above then we assume this has already been done (by a previous
        // incantation of this routine) and not needed again. Note there is
        // therefor NO versioning capability of the dependent libraries without
        // jacking the version number for the actual library being requested.
        if (!retrieveDependencies(lib, errorMsg)) {
            logger.debug("Unable to retrieve dependencies:" + errorMsg[0]);
            throw (new UnsatisfiedLinkError("Unable to retrieve dependencies for " + filename + " : " + errorMsg[0]));
            // return;
        }

        if (!retrieveFile(filename, errorMsg)) {
            logger.debug("Unable to retrieve:" + errorMsg[0]);
            throw (new UnsatisfiedLinkError("Unable to retrieve " + filename + " : " + errorMsg[0]));
            // return;
        }

        // Loadlib the file we just retrieved and saved
        try {
            System.load(fullPathName);
            logger.debug("LINK SUCCESS for " + fullPathName);
            return;
        } catch (UnsatisfiedLinkError e) {
            logger.debug("Unable to link retrieved " + fullPathName + ":" + e);

            // We have done all we can. Throw an exception and return
            throw (new UnsatisfiedLinkError("Cannot link with retrieved library " + fullPathName + ":" + e));
            // return;
        }
    }

    /**
     * Retrieve all the dependencies of a given library. For example if the incoming libname is foo, then the full path of
     * the lib might be libfoo-Solaris-:-2.x-v1.2.so and the dependencies would be listed in the config file as DEP_foo =
     * "libdep1.so" DEP_foo = "libdep2.so"
     */
    private boolean retrieveDependencies(final String libname, final String[] errmsg) {
        final List<String> deps = this.configG.findEntries("DEP_" + libname);

        // See if anything needs to be retrieved
        if ((deps == null) || deps.isEmpty()) {
            return true;
        }

        // Use this string on error
        final String myErr = libname + " dependencies are " + deps;

        for (int i = 0; i < deps.size(); i++) {
            final String file = deps.get(i);
            logger.debug("JNI: Retrieving dependent file " + file);
            if (!retrieveFile(file, errmsg)) {
                errmsg[0] = myErr + ": failed on " + file;
                return false;
            }
        }
        return true;
    }

    /**
     * Actually retrieve the byteStream of the native library over the network and save it to a diskfile.
     */
    public boolean retrieveFile(final String filename, final String[] errmsg) {

        final String osname = System.getProperty("os.name").replace(' ', '_');
        final String theLocation = this.savePath.get(osname);
        final String fullPathName = theLocation + File.separator + filename;
        final byte[] libContents = returnFile(filename, errmsg);

        if (libContents == null) {
            return false;
        }

        // Save the contents into the disk file
        try {
            final FileOutputStream fos = new FileOutputStream(fullPathName);
            final BufferedOutputStream bos = new BufferedOutputStream(fos);
            bos.write(libContents, 0, libContents.length);
            bos.close();
            fos.close();
        } catch (IOException ioe) {
            errmsg[0] = "Cannot write retrieved JNI library to " + fullPathName + ": " + ioe;
            return false;
        }

        return true;
    }

    /**
     * Look up the repository using our directory and retrieve the bytestream over the network with a synchronous call.
     */
    public byte[] returnFile(final String filename, final String[] errmsg) {

        if (this.theDir == null) {
            // Probably instantiated from a static main standalone constructor
            // which should have called returnFile(String,String[],String)
            // instead and specified the key
            errmsg[0] = "No DirectoryPlace available";
            return null;
        }

        try {
            final List<DirectoryEntry> entries = this.theDir.nextKeys("JNI", null, null);
            if (entries == null || entries.size() == 0) {
                errmsg[0] = "No JNI place in directory for:" + filename;
                return null;
            }

            // Just use the first one
            final DirectoryEntry entry = entries.get(0);
            String repositoryKey = entry.getKey();

            // No related place, try a bootstrapping repository
            if (repositoryKey == null) {
                repositoryKey = System.getProperty("emissary.repository");
            }

            // No repository at all. Bail out
            if (repositoryKey == null) {
                errmsg[0] = "JNI.returnFile: cannot retrieve files " + "without a repository.";
                return null;
            }

            // We have a repository of some sort, try using it
            return returnFile(filename, errmsg, repositoryKey);

        } catch (Exception ve) {
            errmsg[0] = "JNI.returnFile: " + ve;
            return null;
        }
    }

    /**
     * Retrieve from the Repository with the specified Key. Can be used for bootstrapping when config files for directories
     * and Repositories might not exist. A non-related repository can be specified in this case and the system will
     * bootstrap from it.
     */
    public byte[] returnFile(final String filename, final String[] errmsg, final String repositoryKey) {

        final String repositoryAddrString = KeyManipulator.getServiceLocation(repositoryKey);

        IJniRepositoryPlace repositoryProxy = null;

        try {
            final String look = repositoryAddrString.substring(repositoryAddrString.indexOf("//"));

            repositoryProxy = (IJniRepositoryPlace) Namespace.lookup(look);
        } catch (Exception e) {
            errmsg[0] = "JNI.returnFile: " + e;
            return null;
        }

        // Ask the repository to send the byte stream of the native
        // library contents
        final byte[] libContents;
        try {
            libContents = repositoryProxy.nativeLibraryDeliver(filename);
        } catch (Exception e) {
            errmsg[0] = "Error calling nativeLibraryDeliver: " + e;
            return null;
        }

        if (libContents == null) {
            errmsg[0] = "Unsuccessful request to repository: " + "got zero bytes";
        }

        return libContents;
    }

    /**
     * Provide access to this OS's default save path
     */
    public String getSavePath() {
        final String osname = System.getProperty("os.name").replace(' ', '_');
        return this.savePath.get(osname);
    }

    /**
     * Provide access to the remote file timestamp
     */
    public long lastModified(final String filename, final String[] errmsg, final String repositoryKey) {

        long stamp = 0L;

        String repositoryAddrString = KeyManipulator.getServiceLocation(repositoryKey);

        if (repositoryAddrString != null && repositoryAddrString.indexOf("//") > -1) {
            repositoryAddrString = repositoryAddrString.substring(repositoryAddrString.indexOf("//"));
        }

        IJniRepositoryPlace repositoryProxy = null;

        try {
            repositoryProxy = (IJniRepositoryPlace) Namespace.lookup(repositoryAddrString);
        } catch (emissary.core.NamespaceException ne) {
            errmsg[0] = "JNI.returnFile: " + ne;
            return stamp;
        }

        // Ask the repository to send the timestamp

        try {
            stamp = repositoryProxy.lastModified(filename);
        } catch (Exception e) {
            errmsg[0] = "Error calling lastModified: " + e;
        }

        return stamp;
    }

    /**
     * Static main used from Makefiles to produce the correct expanded name of the library. Guaranteed to match when the
     * calling program asks for it via the repository mechanism
     */
    public static void main(final String[] args) throws IOException {

        if (args.length < 1) {
            logger.info("usage: java JNI [-L LEVEL] libname");
            return;
        }

        int argpos = 0;
        while ((argpos < args.length) && args[argpos].startsWith("-")) {
            if ("--".equals(args[argpos])) {
                argpos++;
                break;
            }
        }

        final JNI JNI = new JNI();
        String path = JNI.getSavePath();
        if (path != null && !path.endsWith("/")) {
            path += "/";
        }
        for (int i = argpos; i < args.length; i++) {
            System.out.println(path + JNI.filesystemLibraryName(JNI.expandLibraryName(args[i])));
        }
    }
}
