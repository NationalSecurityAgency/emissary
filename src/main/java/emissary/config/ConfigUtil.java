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
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

    /** Supported config suffixes; discovery requires a unique candidate at each location. */
    public static final List<String> CONFIG_FILE_ENDINGS = ResourceReader.CONFIG_SUFFIXES;

    /** Constant string for files that end with {@value} */
    public static final String PROP_FILE_ENDING = ResourceReader.PROP_SUFFIX;

    /** Constant string for files that end with {@value} */
    public static final String XML_FILE_ENDING = ResourceReader.XML_SUFFIX;

    /** Constant string for files that end with {@value} */
    public static final String JS_FILE_ENDING = ResourceReader.JS_SUFFIX;

    /** Constant string for inventory files name prefix */
    public static final String INVENTORY_FILE_PREFIX = "emissary.admin.ClassNameInventory";

    /** Suffixes that resource-name normalization recognizes; config formats plus non-config resources. */
    private static final List<String> RESOURCE_SUFFIXES = buildResourceSuffixes();

    private static List<String> buildResourceSuffixes() {
        final List<String> suffixes = new ArrayList<>(ResourceReader.CONFIG_SUFFIXES);
        suffixes.add(XML_FILE_ENDING);
        suffixes.add(PROP_FILE_ENDING);
        suffixes.add(JS_FILE_ENDING);
        return Collections.unmodifiableList(suffixes);
    }

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
        return Collections.unmodifiableList(configDirs);
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
    @Nullable
    private static String oldStyleName(final String name) {
        // Package-name fallback is not a fallback for an explicit filesystem path.
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        String file = name;
        String suffix = CONFIG_FILE_ENDING;
        final String requested = configFileSuffix(file);
        if (requested != null) {
            suffix = file.substring(file.length() - requested.length());
            file = file.substring(0, file.length() - requested.length());
        }

        if (file.contains("$")) {
            file = file.replace('$', '_');
        }
        if (file.contains(".")) {
            file = file.substring(file.lastIndexOf(".") + 1);
        }
        return file + suffix;
    }

    /**
     * Get the ServiceConfigGuide for the named class
     */
    public static Configurator getConfigInfo(final Class<?> c) throws IOException {
        final String name = c.getName();
        logger.debug("Loading config for (class) {}", name);
        return getConfigInfo(resolveLogicalConfig(name));
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
        for (final String s : preferences) {
            try {
                c = getConfigInfo(s);
                return c;
            } catch (IOException ex) {
                if (isStrictConfigFailure(ex)) {
                    throw new IOException("Preference " + s + " failed to load: " + ex.getMessage(), ex);
                }
                if (findImportFailure(ex) != null) {
                    String exception = ex.getMessage();
                    if (exception == null) {
                        exception = "";
                    }
                    exception = exception.replace("<none>", s);
                    logger.debug("IMPORT_FILE not found in {}", s);
                    throw new IOException(exception, ex);
                }
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
        return getConfigInfo(resolveConfig(name));
    }

    /**
     * Load a selected resource without repeating discovery.
     *
     * @param source the selected config
     * @return the config including optional flavors
     * @throws IOException when loading fails
     */
    public static Configurator getConfigInfo(final ResolvedConfig source) throws IOException {
        try {
            return getConfigInfo(source.openStream(), source.getName());
        } catch (IOException e) {
            if (StructuredConfigParser.isStructuredFile(source.getName()) && !isStrictConfigFailure(e)) {
                throw new StructuredConfigException("Cannot load structured config " + source.getName(), e);
            }
            throw e;
        }
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

        final boolean configFilename = configFileSuffix(name) != null;
        final String[] flavoredNames = configFilename ? getFlavorConfigNames(name) : addFlavors(name);
        for (final String flavoredName : flavoredNames) {
            try {
                logger.debug("Attempting flavor merge on {}", flavoredName);
                final ResolvedConfig source = configFilename ? resolveLogicalConfig(flavoredName) : resolveConfig(flavoredName);
                final Configurator flavoredConfig = getConfigInfo(source);
                scg.merge(flavoredConfig);
                logger.debug("Merged config with {}", flavoredName);
            } catch (IOException iox) {
                if (isStrictConfigFailure(iox)) {
                    throw iox;
                }
                logger.debug("Unable to opt import flavor config {}", flavoredName);
            }
        }
        return scg;
    }

    /**
     * Get the last modified time of a config file resource
     *
     * @param name the name of the config resource
     * @return the lastModified timestamp or -1L if it doesn't exist
     */
    public static long getConfigFileLastModified(final String name) {
        try {
            return resolveConfig(name).getLastModified();
        } catch (IOException e) {
            return -1L;
        }
    }

    /**
     * Get input stream of config data for name
     *
     * @param name the name of the stream to look for
     * @return an InputStream caller must close
     */
    public static InputStream getConfigStream(final String name) throws IOException {
        return resolveExactConfig(name).openStream();
    }

    /**
     * Resolve a filename or discover a suffixless logical name. Existing exact legacy files take precedence.
     *
     * @param name the requested name
     * @return the selected config resource
     * @throws IOException when no config exists or discovery is ambiguous
     */
    public static ResolvedConfig resolveConfig(final String name) throws IOException {
        if (name.startsWith("/") || configFileSuffix(name) != null) {
            return resolveExactConfig(name);
        }
        final ResolvedConfig exact = findNamedResource(name);
        return exact == null ? resolveLogicalConfig(name) : exact;
    }

    /**
     * Discover a unique suffix at the first matching directory or resource package.
     *
     * @param name the suffixless logical name
     * @return the selected resource
     * @throws IOException when no config exists or discovery is ambiguous
     */
    public static ResolvedConfig resolveLogicalConfig(final String name) throws IOException {
        if (name.startsWith("/")) {
            final Path path = Path.of(name).normalize();
            if (path.getParent() != null) {
                final ResolvedConfig source = discoverInDirectory(path.getParent().toString(), path.getFileName().toString());
                if (source != null) {
                    return new ResolvedConfig(name + source.getName().substring(path.getFileName().toString().length()), source.location,
                            source.path);
                }
            }
            throw new ConfigNotFoundException("No config stream available for " + name);
        }
        for (final String dir : getConfigDirs()) {
            final ResolvedConfig source = discoverInDirectory(dir, name);
            if (source != null) {
                return source;
            }
        }
        for (final String resource : toResourceName(name + CONFIG_FILE_ENDING)) {
            final String base = resource.substring(0, resource.length() - CONFIG_FILE_ENDING.length());
            final List<ResolvedConfig> candidates = new ArrayList<>();
            for (final String ending : CONFIG_FILE_ENDINGS) {
                final URL url = new ResourceReader().getResource(base + ending);
                if (url != null) {
                    candidates.add(new ResolvedConfig(name + ending, url));
                }
            }
            final ResolvedConfig source = uniqueConfig(name, candidates);
            if (source != null) {
                return source;
            }
        }
        final String oldStyle = oldStyleName(name + CONFIG_FILE_ENDING);
        if (oldStyle != null) {
            final String base = oldStyle.substring(0, oldStyle.length() - CONFIG_FILE_ENDING.length());
            for (final String dir : getConfigDirs()) {
                final ResolvedConfig source = discoverInDirectory(dir, base);
                if (source != null) {
                    return new ResolvedConfig(name + source.getName().substring(base.length()), source.location, source.path);
                }
            }
        }
        return resolveExactConfig(name);
    }

    /**
     * Get the selected config's name, including its suffix.
     *
     * @param name the requested name
     * @return the selected name
     * @throws IOException when resolution fails
     */
    public static String resolveConfigName(final String name) throws IOException {
        return resolveConfig(name).getName();
    }

    @Nullable
    private static ResolvedConfig findNamedResource(final String name) throws IOException {
        final File f = findConfigFile(name);
        if (f != null) {
            return new ResolvedConfig(name, f.toPath());
        }
        if (name.startsWith("/")) {
            return null;
        }
        for (final String resource : toResourceName(name)) {
            final URL url = new ResourceReader().getResource(resource);
            if (url != null) {
                return new ResolvedConfig(name, url);
            }
        }
        return null;
    }

    private static ResolvedConfig resolveExactConfig(final String name) throws IOException {
        final ResolvedConfig source = findNamedResource(name);
        if (source != null) {
            return source;
        }
        final String oldStyle = oldStyleName(name);
        final File f = oldStyle == null ? null : findConfigFile(oldStyle);
        if (f != null) {
            return new ResolvedConfig(name, f.toPath());
        }
        throw new ConfigNotFoundException("No config stream available for " + name);
    }

    @Nullable
    private static ResolvedConfig discoverInDirectory(final String dir, final String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || name.contains("..")) {
            return null;
        }
        final Path base = Path.of(dir).toAbsolutePath().normalize();
        final Path stem = base.resolve(name).normalize();
        if (!stem.startsWith(base) || stem.equals(base)) {
            return null;
        }
        final String filenameStem = stem.getFileName().toString();
        final File[] files = stem.getParent().toFile().listFiles((parent, filename) -> {
            final String suffix = configFileSuffix(filename);
            return suffix != null && filename.substring(0, filename.length() - suffix.length()).equals(filenameStem);
        });
        final List<ResolvedConfig> candidates = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files);
            for (final File file : files) {
                candidates.add(new ResolvedConfig(name + file.getName().substring(filenameStem.length()), file.toPath()));
            }
        }
        return uniqueConfig(name, candidates);
    }

    @Nullable
    private static ResolvedConfig uniqueConfig(final String name, final List<ResolvedConfig> candidates) throws IOException {
        if (candidates.size() > 1) {
            final List<String> locations = new ArrayList<>();
            for (final ResolvedConfig source : candidates) {
                locations.add(source.location.toExternalForm());
            }
            throw new AmbiguousConfigException("Ambiguous configuration for " + name + ": " + locations);
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Recognize a supported config suffix without changing its filesystem spelling.
     *
     * @param name the filename
     * @return the canonical suffix, or null for a legacy nonstandard name
     */
    @Nullable
    public static String configFileSuffix(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        for (final String suffix : CONFIG_FILE_ENDINGS) {
            if (lower.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }

    /** Find a readable file, keeping relative names within their configured directory. */
    @Nullable
    private static File findConfigFile(final String name) {
        if (name.isEmpty() || name.startsWith("\\")) {
            return null;
        }
        try {
            if (name.startsWith("/")) {
                final File file = Path.of(name).normalize().toFile();
                return file.exists() && file.canRead() ? file : null;
            }
            if (name.contains("..")) {
                return null;
            }
            for (final String dir : getConfigDirs()) {
                final Path base = Path.of(dir).toAbsolutePath().normalize();
                final Path candidate = base.resolve(name).normalize();
                if (candidate.startsWith(base) && !candidate.equals(base)) {
                    final File file = candidate.toFile();
                    if (file.exists() && file.canRead()) {
                        return file;
                    }
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /**
     * Convert a name to work on the classpath as a resource
     *
     * @param name the name to convert
     * @return name with optional config package prepended and s#.#/#g
     */
    private static List<String> toResourceName(final String name) {
        String r = name.replace('.', '/');
        final String suffix = resourceSuffix(name);
        if (suffix != null) {
            r = r.substring(0, r.length() - suffix.length()) + suffix;
        }
        final List<String> prefs = new ArrayList<>();
        if (configPkg != null) {
            prefs.add(configPkg.replace('.', '/') + "/" + r);
        }
        prefs.add(r);
        return prefs;
    }

    /**
     * Find a recognized resource suffix without changing the caller's spelling.
     *
     * @param name the resource name
     * @return the canonical suffix, or null when no known suffix is present
     */
    @Nullable
    private static String resourceSuffix(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        for (final String suffix : RESOURCE_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
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
        if (configFlavors == null || configFlavors.isEmpty()) {
            return null;
        }
        return List.of(configFlavors.split(","));
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
     * Flavor layer names for discovery, preserving arbitrary legacy filename extensions.
     *
     * @param name the base config name
     * @return logical flavor names for supported suffixes, or exact legacy flavor filenames otherwise
     */
    @SuppressWarnings("AvoidObjectArrays")
    public static String[] getFlavorConfigNames(final String name) {
        final String suffix = configFileSuffix(name);
        if (suffix == null) {
            return addFlavors(name);
        }
        if (configFlavors == null || configFlavors.isEmpty()) {
            return new String[0];
        }
        final String base = name.substring(0, name.length() - suffix.length());
        final String[] flavors = configFlavors.split(",");
        for (int i = 0; i < flavors.length; i++) {
            flavors[i] = base + "-" + flavors[i];
        }
        return flavors;
    }

    /**
     * Find a required-import failure in the cause chain.
     *
     * @param failure the loading failure
     * @return the import failure, or null when the failure is unrelated
     */
    @Nullable
    private static ImportNotFoundException findImportFailure(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ImportNotFoundException) {
                return (ImportNotFoundException) cause;
            }
        }
        return null;
    }

    /**
     * Identify failures optional loading must not hide.
     *
     * @param failure the loading failure
     * @return true for ambiguous discovery or a malformed structured config, including wrapped causes
     */
    public static boolean isStrictConfigFailure(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AmbiguousConfigException || cause instanceof StructuredConfigException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Propagate strict config failures through consumers that historically defaulted after legacy IO errors.
     *
     * @param failure the loading failure
     * @throws IllegalStateException when the failure is a strict config error
     */
    public static void rethrowStrictConfigFailure(final Throwable failure) {
        if (isStrictConfigFailure(failure)) {
            throw new IllegalStateException("Invalid configuration: " + failure.getMessage(), failure);
        }
    }

    /**
     * Get the config data for the config file named
     *
     * @param f the named config item (path not necessary, but will be used if present)
     * @return Input Stream, caller must close.
     */
    public static InputStream getConfigData(final String f) throws IOException {
        logger.debug("Request for config data from {}", f);
        return resolveExactConfig(f).openStream();
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
            final File[] files = new File(dir).listFiles((dir1, name) -> name.startsWith(INVENTORY_FILE_PREFIX) && configFileSuffix(name) != null);
            // sort the files, to put emissary.admin.ClassNameInventory.cfg before emissary.admin.ClassNameInventory-blah.cfg
            if (files != null) {
                Arrays.sort(files);
                final Set<String> modules = new HashSet<>();
                for (final File file : files) {
                    final String filename = file.getName();
                    final String module = filename.substring(0, filename.length() - configFileSuffix(filename).length());
                    if (!modules.add(module)) {
                        throw new AmbiguousConfigException("Ambiguous inventory configuration for " + module + " in " + dir);
                    }
                }
                classNameInventory.addAll(Arrays.asList(files));
            }
        }
        // check to make sure we have at least one
        if (classNameInventory.isEmpty()) {
            throw new EmissaryException(String.format("No %s config files found.  No places to start.", INVENTORY_FILE_PREFIX));
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
     * Gets the flavors as specified by the filename.
     * <p>
     * Returns the portion between the last - and .cfg in the file name
     *
     * @param f The file of interest.
     * @return String with parsed flavor name(s)
     */
    static String getFlavorsFromCfgFile(final File f) {
        final String filename = f.getName();
        final String suffix = configFileSuffix(filename);
        if (suffix == null) {
            logger.warn("Not a cfg file: {}", filename);
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

    /** Selected name and location; opening it does not repeat discovery. */
    public static final class ResolvedConfig {
        private final String name;
        private final URL location;
        @Nullable
        private final Path path;

        private ResolvedConfig(final String name, final URL location) {
            this(name, location, null);
        }

        private ResolvedConfig(final String name, final Path path) throws IOException {
            this(name, path.toUri().toURL(), path);
        }

        private ResolvedConfig(final String name, final URL location, @Nullable final Path path) {
            this.name = name;
            this.location = location;
            this.path = path;
        }

        /**
         * Get the selected name.
         *
         * @return the name including its suffix
         */
        public String getName() {
            return name;
        }

        /**
         * Open the selected resource.
         *
         * @return a stream the caller must close
         * @throws IOException when opening fails
         */
        public InputStream openStream() throws IOException {
            return path == null ? location.openStream() : Files.newInputStream(path);
        }

        /**
         * Get the resource modification time.
         *
         * @return the last-modified timestamp in milliseconds, or -1 when unavailable
         */
        public long getLastModified() {
            try {
                if (path != null) {
                    return Files.getLastModifiedTime(path).toMillis();
                }
                final URLConnection connection = location.openConnection();
                return connection.getLastModified();
            } catch (IOException e) {
                return -1L;
            }
        }
    }

    static final class ConfigNotFoundException extends IOException {
        private static final long serialVersionUID = 1L;

        ConfigNotFoundException(final String message) {
            super(message);
        }
    }

    static final class ImportNotFoundException extends IOException {
        private static final long serialVersionUID = 1L;

        ImportNotFoundException(final String message, final IOException cause) {
            super(message, cause);
        }
    }

    static final class AmbiguousConfigException extends IOException {
        private static final long serialVersionUID = 1L;

        AmbiguousConfigException(final String message) {
            super(message);
        }
    }

    static final class StructuredConfigException extends IOException {
        private static final long serialVersionUID = 1L;

        StructuredConfigException(final String message, final IOException cause) {
            super(message, cause);
        }
    }

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
}
