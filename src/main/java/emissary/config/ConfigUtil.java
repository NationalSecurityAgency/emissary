package emissary.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import emissary.core.EmissaryException;
import emissary.util.io.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This configuration utility collection helps to find configuration for various classes and objects. It responds to
 * -Demissary.config.dir=value and treats is as a local directory in which to find configuration files. Failing to find
 * a local file, many of these methods will try to retrieve config data from a resource stream (i.e. the classpath). The
 * package name to use can be prefixed with some package of your choosing by setting -Demissary.config.pkg=value.
 */
public class ConfigUtil {
    /** Our logger */
    protected static final Logger logger = LoggerFactory.getLogger(ConfigUtil.class);

    /** Constant string for files that end with {@value} */
    public static final String CONFIG_FILE_ENDING = ResourceReader.CONFIG_SUFFIX;

    /** Constant string for files that end with {@value} */
    public static final String PROP_FILE_ENDING = ResourceReader.PROP_SUFFIX;

    /** Constant string for files that end with {@value} */
    public static final String XML_FILE_ENDING = ResourceReader.XML_SUFFIX;

    /** Constant string for files that end with {@value} */
    public static final String JS_FILE_ENDING = ResourceReader.JS_SUFFIX;

    /**
     * This property specifies the config override directory. When present, we look here first for config info. If not
     * present, we try and load from the classpath as a resource. The property is set with -D{@value}
     */
    public static final String CONFIG_DIR_PROPERTY = "emissary.config.dir";

    /**
     * This property specifies the config class package prefix specifier Use -D{@value} to change the class value
     */
    public static final String CONFIG_PKG_PROPERTY = "emissary.config.pkg";

    /**
     * This property specified the install/config flavor to run. This will opt-import and merge config files special to the
     * desired flavor allowing multiple configurations to be kept in the same configuration package
     */
    public static final String CONFIG_FLAVOR_PROPERTY = "emissary.config.flavor";

    /**
     * This property is the BIN_DIR, the root directory of all binary scripts and executables used by places.
     */
    public static final String CONFIG_BIN_PROPERTY = "emissary.bin.dir";

    /**
     * This property is the OUTPUT_ROOT, the root directory of local output
     */
    public static final String CONFIG_OUTPUT_ROOT_PROPERTY = "emissary.output.root";

    public static final String PROJECT_BASE_ENV = "PROJECT_BASE";

    /** The package name where config stuff may be found */
    private static String configPkg = null;

    /** The directory where config stuff may be found */
    private static String configDirProperty = null;

    /** The directories where config stuff may be found, searched in order */
    private static List<String> configDirs = null;

    /** The project root directory */
    private static String projectRoot = null;

    /** The output root */
    private static String outputRoot = null;

    /** The bin dir */
    private static String binDir = null;

    /** True if we are running on windows */
    private static boolean isWindows = false;

    /**
     * The configuration flavor, allows multiple layers of config stuff to be stored together in one package or directory,
     * sort of like a mini single inheritance model. It is a comma separated, ordered list of sub-types to try to merge into
     * the current config.
     */
    private static String configFlavors = null;

    private static boolean configErrors = false;

    /**
     * XML bean configuration with Jetty 6 does a poor job of propagating exceptions during configure. Once we update to
     * Jetty 9, evaluate removing this. Otherwise, there is no way I could find to stop the server from starting if there is
     * a configuration error.
     * 
     * TODO: evaluate whether we can change this now that we are on jetty 9
     *
     * This method is used in JettyServer to determine whether to fail startup.
     */
    public static boolean hasConfigErrors() {
        return configErrors;
    }

    /**
     * Perform initialization
     */
    static {
        try {
            initialize();
        } catch (EmissaryException e) {
            logger.error("Error in ConfigUtil static", e);
        }
    }

    /**
     * Remove the trailing slash from a string, if present.
     *
     * @param in The input string.
     * @return If {@code in} ends with a slash, this returns a string with the same content except without the trailing
     *         slash. Otherwise this returns a string with the same content as {@code in}.
     */
    private static String removeTrailingSlash(final String in) {
        if (in.endsWith("/")) {
            return in.substring(0, in.length() - 1);
        } else {
            return in;
        }
    }

    /**
     * Initialize system properties for this class, but make it so that test cases (and other interested parties) can reset
     * system properties and re-call this method when they need to.
     */
    public static void initialize() throws EmissaryException {
        // PlaceStarter.class.getName();
        isWindows = System.getProperty("os.name").indexOf("Window") != -1;

        // throws NPE if not defined
        projectRoot = System.getenv(ConfigUtil.PROJECT_BASE_ENV);

        configDirProperty = System.getProperty(CONFIG_DIR_PROPERTY, "").replace('\\', '/');
        if (configDirProperty.equals("")) {
            logger.error("You must set -Demissary.config.dir, it was empty");
            throw new EmissaryException("-Demissary.config.dir was not set");
        } else if (configDirProperty.equals("/tmp")) {
            logger.error("You probably don't want to use /tmp as the emissary.config.dir");
            throw new EmissaryException("-Demissary.config.dir was /tmp");
        }
        logger.debug("Configured configDirProperty {}", configDirProperty);
        outputRoot = System.getProperty(CONFIG_OUTPUT_ROOT_PROPERTY);
        binDir = System.getProperty(CONFIG_BIN_PROPERTY);

        configFlavors = System.getProperty(CONFIG_FLAVOR_PROPERTY, null);
        configPkg = System.getProperty(CONFIG_PKG_PROPERTY, configPkg);

        configDirs = new ArrayList<String>();
        final List<String> dirs = Arrays.asList(configDirProperty.split(","));
        for (final String dir : dirs) {
            final String dirNoTrailingSlash = removeTrailingSlash(dir);
            // only add directories that exist
            if (Files.exists(Paths.get(dirNoTrailingSlash))) {
                configDirs.add(dirNoTrailingSlash);
            } else {
                logger.warn("Directory configured but didn't exist: " + dirNoTrailingSlash);
            }
        }
    }

    /**
     * Give the project root directory
     */
    public static String projectRootDirectory() {
        return projectRoot;
    }

    /**
     * Return project base
     */
    public static String getProjectBase() {
        return projectRoot;
    }

    /**
     * Return output root
     */
    public static String getOutputRoot() {
        return outputRoot;
    }

    /**
     * Return bin dir
     */
    public static String getBinDir() {
        return binDir;
    }

    /**
     * Get a List of config directories.
     */
    public static List<String> getConfigDirs() {
        if (configDirs == null) {
            throw new RuntimeException("No config directory specified");
        }
        return configDirs;
    }

    /**
     * Get the first config dir.
     * <p>
     * If there is only one config dir, it just returned
     */
    public static String getFirstConfigDir() {
        return getConfigDirs().get(0);
    }

    /**
     * Get the named config file from the config directory
     *
     * @param file the config file name to get, path part ignored and replace with normal config dir if not absolute
     */
    public static String getConfigFile(final String file) {

        if ((!isWindows && file.startsWith("/")) || (isWindows && file.charAt(1) == ':')) {
            return file;
        }

        if (getConfigDirs().size() > 1) {
            final List<String> candidates = new ArrayList<>();
            for (final String dir : getConfigDirs()) {
                final String fname = dir + "/" + file;
                if (Files.exists(Paths.get(fname))) {
                    candidates.add(fname);
                }
            }
            if (candidates.isEmpty()) {
                logger.debug("No file found in any of the configured directories: " + file);
                return getFirstConfigDir() + "/" + file;
            } else if (candidates.size() > 1) {
                logger.error("Multiple files found in the configured directories: " + file + ", returning the first.");
            }
            logger.trace("Returning {}", candidates.get(0));
            return candidates.get(0);
        } else { // much more efficient to do this, no file check each time
            final String cfgFile = getFirstConfigDir() + "/" + file;
            logger.trace("Returning {}", cfgFile);
            return cfgFile;
        }
    }

    /**
     * Get the named config file from the named config path
     *
     * @param path the config path to use
     * @param file the file to get,
     */
    public static String getConfigFile(final String path, final String file) {
        if (file.startsWith("/") || (isWindows && file.charAt(1) == ':')) {
            return file;
        }

        return removeTrailingSlash(path) + "/" + file;
    }

    /**
     * Get the config file for the specified name without package naming
     */
    private static String getOldStyleConfigFile(final String name) {
        String file = name;
        // Chomp the file suffix
        if (file.endsWith(CONFIG_FILE_ENDING)) {
            file = file.substring(0, file.length() - CONFIG_FILE_ENDING.length());
        }

        if (file.indexOf("$") > -1) {
            file = file.replace('$', '_');
        }
        if (file.indexOf(".") > -1) {
            file = file.substring(file.lastIndexOf(".") + 1);
        }
        return getConfigFile(file + CONFIG_FILE_ENDING);
    }

    /**
     * Get the ServiceConfigGuide for the named class
     */
    public static Configurator getConfigInfo(final Class<?> c) throws IOException {
        final String name = c.getName() + CONFIG_FILE_ENDING;
        logger.debug("Loading config for (class) {}", name);
        return getConfigInfo(getConfigStream(name), name);
    }

    /**
     * Get configurator by trying the list of preferences in order and using the first one that is found. Sometimes and
     * array signature can be easier to use from a static context.
     *
     * @param preferences array of string names to try
     * @return the configurator
     * @throws IOException if none of the prefs can be found
     */
    public static Configurator getConfigInfo(final String[] preferences) throws IOException {
        return getConfigInfo(Arrays.asList(preferences));
    }

    /**
     * Get configurator by trying the list of preferences in order and using the first one that is found.
     *
     * @param preferences string names of configs to try
     * @return the configurator
     * @throws IOException if none of the prefs can be found
     */
    public static Configurator getConfigInfo(final List<String> preferences) throws IOException {
        Configurator c = null;
        for (final String s : preferences) {
            try {
                c = getConfigInfo(s);
                return c;
            } catch (IOException ex) {
                logger.debug("Preference {} not found", s);
            }
        }
        throw new IOException("None of the " + preferences.size() + " preferences could be found: " + preferences);
    }

    /**
     * Get Configurator for named object
     *
     * @param name object name to get config info for
     */
    public static Configurator getConfigInfo(final String name) throws IOException {
        logger.debug("Loading config for (string) {}", name);
        return getConfigInfo(getConfigStream(name), name);
    }

    /**
     * Get the configurator on the specified stream
     *
     * @param is the stream of data
     * @return configurator object
     */
    public static Configurator getConfigInfo(final InputStream is) throws IOException {
        return getConfigInfo(is, "<none>");
    }

    /**
     * Get the last modified time of a config file resource
     *
     * @param name the name of the config resource
     * @return the lastModified timestamp or -1L if it doesn't exist
     */
    public static long getConfigFileLastModified(final String name) {
        final String sname = getConfigFile(name);
        final File f = new File(sname);
        if (f.exists() && f.canRead()) {
            return f.lastModified();
        }
        return -1L;
    }

    /**
     * Get input stream of config data for name
     *
     * @param name the name of the stream to look for
     * @return an InputStream caller must close
     */
    public static InputStream getConfigStream(final String name) throws IOException {
        // Try the new style override name first ( with package )
        String sname = getConfigFile(name);
        File f = new File(sname);
        if (f.exists() && f.canRead()) {
            logger.debug("Found config data as file {}", f.getPath());
            return new FileInputStream(f);
        }
        logger.debug("No file config found using new style {}", f.getName());

        // Try the classpath loader
        final List<String> reznames = toResourceName(name);
        for (final String rezname : reznames) {
            final URL url = new ResourceReader().getResource(rezname);
            if (url != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Found config data as resource {}", url.toExternalForm());
                }

                try {
                    final InputStream is = url.openStream();
                    return is;
                } catch (IOException ex) {
                    logger.warn("IOException opening stream for resource for " + rezname);
                }
            }

            logger.debug("No config data as resource for {}", rezname);
        }
        logger.trace("No stream config found using {}", reznames);

        // Try the old style override name ( no package )
        sname = getOldStyleConfigFile(name);
        f = new File(sname);
        if (f.exists() && f.canRead()) {
            logger.debug("Found config data as file old style {}", f.getPath());
            return new FileInputStream(f);
        }
        logger.debug("No file config found using old style {}", f.getName());

        throw new IOException("No config stream available for " + name);
    }

    /**
     * Convert a name to work on the classpath as a resource
     *
     * @param name the name to convert
     * @return name with optional config package prepended and s#.#/#g
     */
    private static List<String> toResourceName(final String name) {
        String r = name.replace('.', '/');
        if (r.toUpperCase().endsWith("/CFG")) {
            r = r.substring(0, r.length() - CONFIG_FILE_ENDING.length()) + CONFIG_FILE_ENDING;
        } else if (r.toUpperCase().endsWith("/XML")) {
            r = r.substring(0, r.length() - XML_FILE_ENDING.length()) + XML_FILE_ENDING;
        } else if (r.toUpperCase().endsWith("/PROPERTIES")) {
            r = r.substring(0, r.length() - PROP_FILE_ENDING.length()) + PROP_FILE_ENDING;
        } else if (r.toUpperCase().endsWith("/JS")) {
            r = r.substring(0, r.length() - JS_FILE_ENDING.length()) + JS_FILE_ENDING;
        }
        final List<String> prefs = new ArrayList<String>();
        if (configPkg != null) {
            prefs.add(configPkg.replace('.', '/') + "/" + r);
        }
        prefs.add(r);
        return prefs;
    }

    /**
     * Get the named resource as a Properties object
     */
    public static Properties getPropertyInfo(final String name) throws IOException {
        final Properties props = new Properties();
        final File f = new File(getConfigFile(name));
        InputStream is = null;

        try {
            if (f.exists() && f.canRead()) {
                is = new FileInputStream(f);
            } else {
                final List<String> cnameprefs = toResourceName(name);
                for (final String cname : cnameprefs) {
                    is = new ResourceReader().getResourceAsStream(cname);
                    if (is != null) {
                        break;
                    }
                }
            }

            if (is != null) {
                props.load(is);
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return props;
    }

    /**
     * Return the in-use config flavors
     */
    public static List<String> getFlavors() {
        if (configFlavors == null) {
            return null;
        }
        return Arrays.asList(configFlavors);
    }

    /**
     * Add the current config Flavor to the name of the resource passed in. E.g. emissary.pkg.Foo.cfg =&gt;
     * emissary.pkg.Foo-${FLAVOR}.cfg
     *
     * @param name the base resource or config name
     * @return the name with the flavor in it
     */
    public static String[] addFlavors(final String name) {
        if (configFlavors == null || configFlavors.length() == 0) {
            return new String[0];
        }

        final int pos = name.lastIndexOf('.');
        final String base = pos > -1 ? name.substring(0, pos) : name;
        final String suffix = pos > -1 ? name.substring(pos) : "";
        final String[] flavor = configFlavors.split(",");
        final String[] flavoredNames = new String[flavor.length];
        for (int i = 0; i < flavor.length; i++) {
            flavoredNames[i] = base + "-" + flavor[i] + suffix;
        }
        return flavoredNames;
    }

    /**
     * Get the configurator on the specified stream
     *
     * @param is the stream of data
     * @param name the name of the stream for debugging
     * @return configurator object
     */
    public static Configurator getConfigInfo(final InputStream is, final String name) throws IOException {
        final ServiceConfigGuide scg = new ServiceConfigGuide(is, name);

        final String[] flavoredNames = addFlavors(name);
        for (final String flavoredName : flavoredNames) {
            try {
                logger.debug("Attempting flavor merge on {}", flavoredName);
                final Configurator flavoredConfig = getConfigInfo(flavoredName);
                scg.merge(flavoredConfig);
                logger.debug("Merged config with {}", flavoredName);
            } catch (IOException iox) {
                logger.debug("Unable to opt import flavor config {}", flavoredName);
            }
        }
        return scg;
    }

    /**
     * Get the config data for the config file named
     *
     * @param f the named config item (path not necessary, but will be used if present)
     * @return Input Stream, caller must close.
     */
    public static InputStream getConfigData(final String f) throws IOException {
        logger.debug("Request for config data from {}", f);

        // Add config.dir part if not already absolute
        final String filename = (f.startsWith("/") || (isWindows && f.charAt(1) == ':')) ? f : getConfigFile(f);

        return new FileInputStream(filename);
    }

    /**
     * Gets all MasterClassNames from configured file.
     * <p>
     * For a single entry in 'emissary.config.dir' or comma separated list of config directories, every file that starts
     * with 'emissary.admin.MasterClassNames' will be combined into a Configurator. This means files like
     * 'emissary.admin.MasterClassNames.cfg', 'emissary.admin.MasterClassNames-module1.cfg' and
     * 'emissary.admin.MasterClassNames-whatever.cfg' will be used. The concept of flavoring no longer applies to the
     * MasterClassNames.
     *
     * @return Configurator with all emissary.admin.MasterClassNames
     * @throws IOException If there is some I/O problem.
     */
    public static Configurator getMasterClassNames() throws IOException, EmissaryException {
        final List<File> masterClassNames = new ArrayList<>();
        for (final String dir : getConfigDirs()) {
            final File[] files = new File(dir).listFiles(new FilenameFilter() {
                @Override
                public boolean accept(final File dir, final String name) {
                    return name.startsWith("emissary.admin.MasterClassNames") && name.endsWith(".cfg");
                }
            });
            // sort the files, to put emissary.admin.MasterClassNames.cfg before emssary.admin.MasterClassNames-blah.cfg
            Arrays.sort(files);
            masterClassNames.addAll(Arrays.asList(files));
        }
        // check to make sure we have at least one
        // TODO make a test for this
        if (masterClassNames.size() < 1) {
            throw new EmissaryException("No emissary.admin.MasterClassNames.cfg files found.  No places to start");
        }

        ServiceConfigGuide scg = null;
        for (final File f : masterClassNames) {
            if (!f.exists() || !f.canRead()) {
                logger.warn("Could not read MasterClassNames from " + f.getAbsolutePath());
            } else {
                logger.debug("Reading MasterClassNames from {}", f.getAbsolutePath());
            }
            if (null != configFlavors) {
                final String cfgFlavor = getFlavorsFromCfgFile(f);
                if (configFlavors.equals(cfgFlavor) || Arrays.asList(configFlavors.split(",")).contains(cfgFlavor)) {
                    logger.warn("Config file {} appeared to be flavored with {}.", f.getName(), cfgFlavor);
                }
            }
            if (scg == null) { // first one
                scg = new ServiceConfigGuide(new FileInputStream(f), "MasterClassNames");
            } else {
                final Set<String> existingKeys = scg.entryKeys();
                final Configurator scgToMerge = new ServiceConfigGuide(new FileInputStream(f), "MasterClassNames");
                boolean noErrorsForFile = true;
                for (final String key : scgToMerge.entryKeys()) {
                    if (existingKeys.contains(key)) {
                        logger.error("Tried to overwrite existing key from MasterClassNames:" + key + " in " + f.getAbsolutePath());
                        noErrorsForFile = false;
                        // System.exit(43); // this is swallowed in JettyServer in jetty 6
                    }
                }
                // only merge if there are no errors
                if (noErrorsForFile) {
                    scg.merge(scgToMerge);
                } else {
                    configErrors = true;
                }
            }
        }
        return scg;
    }

    /**
     * Gets the flavors as specified by the filename.
     * <p>
     * Returns the portion between the last - and .cfg in the file name
     *
     * @param f The file of interest.
     * @return String with parsed flavor name(s)
     */
    static String getFlavorsFromCfgFile(final File f) {
        final String filename = f.getName();
        if (!filename.endsWith(".cfg")) {
            logger.warn("Not a cfg file: {}", filename);
            return "";
        }
        final String[] parts = filename.split("-");
        if (parts.length == 1) {
            // no flavor
            return "";
        }
        if (parts.length > 2) {
            logger.warn("Filename {} had multiple - characters, using the last to determin the flavor", filename);
        }
        return parts[parts.length - 1].replaceAll(".cfg", "");
    }

    /** This class is not meant to be instantiated. */
    private ConfigUtil() {}

    /**
     * Read a merged config setup and print the results
     */
    public static void main(final String[] args) {
        if (args.length < 1) {
            System.err.println("usage: java " + ConfigUtil.class.getName() + " configfile");
            return;
        }

        for (int j = 0; j < args.length; j++) {
            try {
                final Configurator config = ConfigUtil.getConfigInfo(args[j]);
                System.out.println("Config File: " + args[j]);
                for (final ConfigEntry c : config.getEntries()) {
                    System.out.println(c.getKey() + ": " + c.getValue());
                }
                System.out.println("---");
            } catch (IOException e) {
                System.err.println("Cannot process " + args[j] + ":" + e);
            }
        }
    }
}
