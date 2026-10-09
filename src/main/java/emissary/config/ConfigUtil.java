package emissary.config;

import emissary.core.EmissaryException;
import emissary.util.io.ResourceReader;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * This configuration utility collection helps to find configuration for various classes and objects. It responds to
 * -Demissary.config.dir=value and treats it as a local directory in which to find configuration files. Failing to find
 * a local file, many of these methods will try to retrieve config data from a resource stream (i.e. the classpath). The
 * package name to use can be prefixed with some package of your choosing by setting -Demissary.config.pkg=value.
 */
@SuppressWarnings("NonFinalStaticField")
public class ConfigUtil {
    /** Our logger */
    protected static final Logger logger = LoggerFactory.getLogger(ConfigUtil.class);

    /** Constant string for files that end with {@value} */
    public static final String CONFIG_FILE_ENDING = ResourceReader.CONFIG_SUFFIX;

    /** Config file endings in lookup order: legacy first, then structured formats. */
    public static final List<String> CONFIG_FILE_ENDINGS = ResourceReader.CONFIG_SUFFIXES;

    /** Constant string for files that end with {@value} */
    public static final String PROP_FILE_ENDING = ResourceReader.PROP_SUFFIX;

    /** Constant string for files that end with {@value} */
    public static final String XML_FILE_ENDING = ResourceReader.XML_SUFFIX;

    /** Constant string for files that end with {@value} */
    public static final String JS_FILE_ENDING = ResourceReader.JS_SUFFIX;

    /** Constant string for inventory files name prefix */
    public static final String INVENTORY_FILE_PREFIX = "emissary.admin.ClassNameInventory";

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

    public static final String CLASS_NAME = "CLASS_NAME";

    /** The package name where config stuff may be found */
    @Nullable
    private static String configPkg = null;

    /** The directory where config stuff may be found */
    @Nullable
    private static String configDirProperty = null;

    /** The directories where config stuff may be found, searched in order */
    @Nullable
    private static List<String> configDirs = null;

    /** The project root directory */
    @Nullable
    private static String projectRoot = null;

    /** The output root */
    @Nullable
    private static String outputRoot = null;

    /** The bin dir */
    @Nullable
    private static String binDir = null;

    /**
     * The configuration flavor, allows multiple layers of config stuff to be stored together in one package or directory,
     * sort of like a mini single inheritance model. It is a comma separated, ordered list of subtypes to try to merge into
     * the current config.
     */
    @Nullable
    private static String configFlavors = null;

    /*
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
     *         slash. Otherwise, this returns a string with the same content as {@code in}.
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

        configDirs = new ArrayList<>();
        final String[] dirs = configDirProperty.split(",");
        for (final String dir : dirs) {
            final String dirNoTrailingSlash = removeTrailingSlash(dir);
            // only add directories that exist
            if (Files.exists(Path.of(dirNoTrailingSlash))) {
                configDirs.add(dirNoTrailingSlash);
            } else {
                logger.warn("Directory configured but didn't exist: {}", dirNoTrailingSlash);
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
            throw new IllegalStateException("No config directory specified");
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

        if (file.startsWith("/")) {
            return file;
        }

        if (getConfigDirs().size() > 1) {
            final List<String> candidates = new ArrayList<>();
            for (final String dir : getConfigDirs()) {
                final String fname = dir + "/" + file;
                if (Files.exists(Path.of(fname))) {
                    candidates.add(fname);
                }
            }
            if (candidates.isEmpty()) {
                logger.debug("No file found in any of the configured directories: {}", file);
                return getFirstConfigDir() + "/" + file;
            } else if (candidates.size() > 1) {
                logger.error("Multiple files found in the configured directories: {}, returning the first.", file);
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
        if (file.startsWith("/")) {
            return file;
        }

        return removeTrailingSlash(path) + "/" + file;
    }

    /**
     * Get the config file for the specified name without package naming
     */
    private static String getOldStyleConfigFile(final String name) {
        String file = name;
        String suffix = CONFIG_FILE_ENDING;
        // Chomp the file suffix
        final String requested = configFileSuffix(file);
        if (requested != null) {
            file = file.substring(0, file.length() - requested.length());
            suffix = requested;
        }

        if (file.contains("$")) {
            file = file.replace('$', '_');
        }
        if (file.contains(".")) {
            file = file.substring(file.lastIndexOf(".") + 1);
        }
        return getConfigFile(file + suffix);
    }

    /**
     * Get the ServiceConfigGuide for the named class
     */
    public static Configurator getConfigInfo(final Class<?> c) throws IOException {
        final String base = c.getName();
        final List<String> prefs = new ArrayList<>();
        for (final String ending : CONFIG_FILE_ENDINGS) {
            prefs.add(base + ending);
        }
        logger.debug("Loading config for (class) {}", prefs);
        return getConfigInfo(prefs);
    }

    /**
     * Get configurator by trying the list of preferences in order and using the first one that is found. Sometimes and
     * array signature can be easier to use from a static context.
     *
     * @param preferences array of string names to try
     * @return the configurator
     * @throws IOException if none of the prefs can be found
     * @deprecated use {@link #getConfigInfo(List)}
     */
    @Deprecated
    @SuppressWarnings("AvoidObjectArrays")
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
        Configurator c;
        IOException loadFailure = null;
        for (final String s : preferences) {
            try {
                c = getConfigInfo(s);
                return c;
            } catch (ConfigNotFoundException ex) {
                logger.debug("Preference {} not found", s);
            } catch (IOException ex) {
                String exception = ex.getMessage();
                if (exception == null) {
                    exception = "";
                }
                if (exception.contains("IMPORT_FILE")) {
                    exception = exception.replace("<none>", s);
                    logger.debug("IMPORT_FILE not found in {}", s);
                    throw new IOException(exception);
                }
                // Found but failed to load (e.g., a parse error): log it now and remember the first
                // one so the thrown exception is the real problem, not a "not found" summary
                logger.warn("Preference {} failed to load", s, ex);
                if (loadFailure == null) {
                    loadFailure = ex;
                } else {
                    loadFailure.addSuppressed(ex);
                }
            }
        }
        if (loadFailure != null) {
            throw loadFailure;
        }
        throw new IOException("None of the " + preferences.size() + " preferences could be found/loaded: " + preferences);
    }

    /**
     * Get Configurator for named object
     *
     * @param name object name to get config info for
     */
    public static Configurator getConfigInfo(final String name) throws IOException {
        logger.debug("Loading config for (string) {}", name);
        final String resolved = resolveConfigName(name);
        return getConfigInfo(getConfigStream(resolved), resolved);
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
     * Resolve the requested config name to an existing file across formats, falling back to the request unchanged.
     *
     * @param name the requested config name
     * @return the resolved config name, or the requested name when no other format holds it
     */
    public static String resolveConfigName(final String name) {
        final String resolved = resolveName(name);
        return resolved != null ? resolved : name;
    }

    /**
     * Resolve the requested config name
     *
     * @param name the requested config name
     * @return the resolved config name, or null when no fallback applies
     */
    @Nullable
    private static String resolveName(final String name) {
        if (!CONFIG_FILE_ENDING.equals(configFileSuffix(name))) {
            return null;
        }
        final String base = name.substring(0, name.length() - CONFIG_FILE_ENDING.length());
        // Config-dir files first, so a deployment override wins over a shipped default in any format
        for (final String ending : CONFIG_FILE_ENDINGS) {
            final File f = new File(getConfigFile(base + ending));
            if (f.exists() && f.canRead()) {
                return base + ending;
            }
        }
        // Then the classpath, same order
        for (final String ending : CONFIG_FILE_ENDINGS) {
            for (final String rezname : toResourceName(base + ending)) {
                if (new ResourceReader().getResource(rezname) != null) {
                    return base + ending;
                }
            }
        }
        // Then old-style files, same order
        for (final String ending : CONFIG_FILE_ENDINGS) {
            final File f = new File(getOldStyleConfigFile(base + ending));
            if (f.exists() && f.canRead()) {
                return base + ending;
            }
        }
        return null;
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
            return Files.newInputStream(f.toPath());
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
                    return url.openStream();
                } catch (IOException ex) {
                    logger.warn("IOException opening stream for resource for {}", rezname);
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
            return Files.newInputStream(f.toPath());
        }
        logger.debug("No file config found using old style {}", f.getName());

        throw new ConfigNotFoundException("No config stream available for " + name);
    }

    /**
     * Convert a name to work on the classpath as a resource
     *
     * @param name the name to convert
     * @return name with optional config package prepended and s#.#/#g
     */
    private static List<String> toResourceName(final String name) {
        String r = name.replace('.', '/');
        final String[][] suffixRepairs = {
                {"/CFG", CONFIG_FILE_ENDING},
                {"/YAML", ResourceReader.YAML_SUFFIX},
                {"/YML", ResourceReader.YML_SUFFIX},
                {"/TOML", ResourceReader.TOML_SUFFIX},
                {"/XML", XML_FILE_ENDING},
                {"/PROPERTIES", PROP_FILE_ENDING},
                {"/JS", JS_FILE_ENDING},
        };
        final String upper = r.toUpperCase(Locale.ROOT);
        for (final String[] repair : suffixRepairs) {
            if (upper.endsWith(repair[0])) {
                r = r.substring(0, r.length() - repair[0].length()) + repair[1];
                break;
            }
        }
        final List<String> prefs = new ArrayList<>();
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
        try (InputStream is = getPropertyStream(name, new File(getConfigFile(name)))) {
            if (is != null) {
                props.load(is);
            }
        }
        return props;
    }

    protected static InputStream getPropertyStream(final String name, final File f) throws IOException {
        InputStream is = null;
        if (f.exists() && f.canRead()) {
            is = Files.newInputStream(f.toPath());
        } else {
            final List<String> cnameprefs = toResourceName(name);
            for (final String cname : cnameprefs) {
                is = new ResourceReader().getResourceAsStream(cname);
                if (is != null) {
                    break;
                }
            }
        }
        return is;
    }

    /**
     * Return the in-use config flavors
     */
    @Nullable
    public static List<String> getFlavors() {
        if (configFlavors == null) {
            return null;
        }
        return Collections.singletonList(configFlavors);
    }

    /**
     * Add the current config Flavor to the name of the resource passed in. E.g. emissary.pkg.Foo.cfg =&gt;
     * emissary.pkg.Foo-${FLAVOR}.cfg
     *
     * @param name the base resource or config name
     * @return the name with the flavor in it
     */
    @SuppressWarnings("AvoidObjectArrays")
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
     * Get the config data for the config file named
     *
     * @param f the named config item (path not necessary, but will be used if present)
     * @return Input Stream, caller must close.
     */
    public static InputStream getConfigData(final String f) throws IOException {
        logger.debug("Request for config data from {}", f);

        // Add config.dir part if not already absolute
        final String filename = f.startsWith("/") ? f : getConfigFile(f);

        return Files.newInputStream(Path.of(filename));
    }

    /**
     * Gets all ClassNameInventory from configured file.
     * <p>
     * For a single entry in 'emissary.config.dir' or comma separated list of config directories, every file that starts
     * with 'emissary.admin.ClassNameInventory' will be combined into a Configurator. This means files like
     * 'emissary.admin.ClassNameInventory.cfg', 'emissary.admin.ClassNameInventory-module1.cfg' and
     * 'emissary.admin.ClassNameInventory-whatever.cfg' will be used. The concept of flavoring no longer applies to the
     * ClassNameInventory.
     *
     * @return Configurator with all emissary.admin.ClassNameInventory
     * @throws IOException If there is some I/O problem.
     * @throws EmissaryException If no config files are found.
     */
    public static Configurator getClassNameInventory() throws IOException, EmissaryException {
        final List<File> classNameInventory = new ArrayList<>();
        for (final String dir : getConfigDirs()) {
            final File[] files = new File(dir).listFiles(
                    (dir1, name) -> name.startsWith(INVENTORY_FILE_PREFIX) && isConfigFile(name));
            // sort the files, to put emissary.admin.ClassNameInventory.cfg before emissary.admin.ClassNameInventory-blah.cfg
            if (files != null) {
                Arrays.sort(files);
                classNameInventory.addAll(Arrays.asList(files));
            }
        }
        // check to make sure we have at least one
        if (classNameInventory.isEmpty()) {
            throw new EmissaryException(String.format("No %s{.cfg,.yaml,.yml,.toml} files found.  No places to start.",
                    INVENTORY_FILE_PREFIX));
        }

        ServiceConfigGuide scg = null;
        for (final File f : classNameInventory) {
            if (!f.exists() || !f.canRead()) {
                logger.warn("Could not read ClassNameInventory from {}", f.getAbsolutePath());
            } else {
                logger.debug("Reading ClassNameInventory from {}", f.getAbsolutePath());
            }
            if (null != configFlavors) {
                final String cfgFlavor = getFlavorsFromCfgFile(f);
                if (configFlavors.equals(cfgFlavor) || Arrays.asList(configFlavors.split(",")).contains(cfgFlavor)) {
                    logger.warn("Config file {} appeared to be flavored with {}.", f.getName(), cfgFlavor);
                }
            }
            if (scg == null) { // first one
                scg = new ServiceConfigGuide(Files.newInputStream(f.toPath()), f.getName());
            } else {
                final Set<String> existingKeys = scg.entryKeys();
                final Configurator scgToMerge = new ServiceConfigGuide(Files.newInputStream(f.toPath()), f.getName());
                boolean noErrorsForFile = true;
                for (final String key : scgToMerge.entryKeys()) {
                    if (existingKeys.contains(key)) {
                        logger.error("Tried to overwrite existing key from ClassNameInventory:{} in {}", key, f.getAbsolutePath());
                        noErrorsForFile = false;
                        // System.exit(43); // this is swallowed in JettyServer in jetty 6
                    }
                }
                // only merge if there are no errors
                if (noErrorsForFile) {
                    scg.merge(scgToMerge);
                }
            }
        }
        return scg;
    }

    /**
     * Whether a filename is a config file
     *
     * @param filename the config name to check
     * @return true for config file names
     */
    static boolean isConfigFile(final String filename) {
        return configFileSuffix(filename) != null;
    }

    /**
     * The config suffix of a filename
     *
     * @param filename the config name to check
     * @return the lowercase suffix, or null if it has none.
     */
    @Nullable
    public static String configFileSuffix(final String filename) {
        final String lower = filename.toLowerCase(Locale.ROOT);
        for (final String ending : CONFIG_FILE_ENDINGS) {
            if (lower.endsWith(ending)) {
                return ending;
            }
        }
        return null;
    }

    /**
     * Gets the flavors as specified by the filename.
     * <p>
     * Returns the portion between the last - and the config suffix in the file name
     *
     * @param f The file of interest.
     * @return String with parsed flavor name(s)
     */
    static String getFlavorsFromCfgFile(final File f) {
        final String filename = f.getName();
        final String suffix = configFileSuffix(filename);
        if (suffix == null) {
            logger.warn("Not a config file: {}", filename);
            return "";
        }
        final String[] parts = filename.split("-");
        if (parts.length == 1) {
            // no flavor
            return "";
        }
        if (parts.length > 2) {
            logger.warn("Filename {} had multiple - characters, using the last to determine the flavor", filename);
        }
        return parts[parts.length - 1].substring(0, parts[parts.length - 1].length() - suffix.length());
    }

    /**
     * Instantiates an object from the class defined under the {@code "CLASS_NAME"} configuration of the given name. The
     * created instance must be assignable to the specified type.
     *
     * @param expectedType the base type the instantiated class must extend or implement
     * @param name the object name to get Configurator info for
     * @return a new instance of the specified class, cast to the expected type
     * @param <T> the expected superclass or interface type
     */
    public static <T> T instantiateFromConfig(Class<T> expectedType, final String name) {
        try {
            return instantiateFromConfig(expectedType, getConfigInfo(name));
        } catch (IOException e) {
            throw new IllegalStateException("Import file not found: " + name, e);
        }
    }

    /**
     * Instantiates an object from the class defined under the {@code "CLASS_NAME"} configuration of the given
     * {@link Configurator}. The created instance must be assignable to the specified type.
     *
     * @param expectedType the base type the instantiated class must extend or implement
     * @param cfg the Configurator object
     * @return a new instance of the specified class, cast to the expected type
     * @param <T> the expected superclass or interface type
     */
    public static <T> T instantiateFromConfig(Class<T> expectedType, Configurator cfg) {
        String className = cfg.findRequiredStringEntry(CLASS_NAME);
        try {
            Class<?> clz = Class.forName(className);
            Object instance = clz.getDeclaredConstructor(Configurator.class).newInstance(cfg);
            return expectedType.cast(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate " + className, e);
        }
    }

    /**
     * Returns a {@link Configurator} containing only entries with a given key prefix.
     *
     * @param cfg The {@link Configurator} to extract entries from
     * @param keyPrefix The key prefix that filters config entries
     * @return A new {@link Configurator} object
     */
    public static Configurator getSubConfig(Configurator cfg, String keyPrefix) {
        Configurator subConfigs = new ServiceConfigGuide();
        cfg.findStringMatchMultiMap(keyPrefix, true, true)
                .forEach((key, value) -> subConfigs.addEntries(key, new ArrayList<>(value)));
        return subConfigs;
    }

    /** This class is not meant to be instantiated. */
    private ConfigUtil() {}

    /**
     * Read a merged config setup and print the results
     */
    public static void main(final String[] args) {
        if (args.length < 1) {
            logger.error("usage: java {} configfile", ConfigUtil.class.getName());
            return;
        }

        for (String arg : args) {
            try {
                final Configurator config = ConfigUtil.getConfigInfo(arg);
                logger.info("Config File: {}", arg);
                for (final ConfigEntry c : config.getEntries()) {
                    logger.info("{}: {}", c.getKey(), c.getValue());
                }
                logger.info("---");
            } catch (IOException e) {
                logger.error("Cannot process {}: {}", arg, e.getLocalizedMessage());
            }
        }
    }

    /**
     * Thrown when a config name exists in no configured location, so callers can tell "nothing there" apart from "found but
     * could not be loaded" (e.g., a parse error).
     */
    static final class ConfigNotFoundException extends IOException {
        private static final long serialVersionUID = 1L;

        ConfigNotFoundException(final String message) {
            super(message);
        }
    }
}
