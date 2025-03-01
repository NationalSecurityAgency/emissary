package emissary.config;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * This class implements the Configurator interface for services within the Emissary framework.
 */

public class ServiceConfigGuide implements Configurator, Serializable {

    static final long serialVersionUID = 3906838615422657150L;
    public static final char SLASH = '/';
    public static final char COLON = ':';
    public static final String DOUBLESLASH = "//";

    protected static final Logger logger = LoggerFactory.getLogger(ServiceConfigGuide.class);

    protected static final String DEFAULT_FILE_NAME = "default.cfg";
    protected static final String POST_FILE_NAME = "post.cfg";

    // Used on the RHS to make a null assignment
    // Obsolete, use @{NULL}
    protected static final String NULL_VALUE = "<null>";

    // Hold all service specific parameters in a list
    protected List<ConfigEntry> serviceParameters = new ArrayList<>();

    // Hold all remove config entries, operator of !=
    protected List<ConfigEntry> removeParameters = new ArrayList<>();

    protected String operator;

    // Start and end to a dynamic substitution
    protected static final String VSTART = "@{";
    protected static final String VEND = "}";

    // Shared map of all environment properties
    // Access them with @ENV{'os.name'} for example
    // This is obsolete, all values from properties and
    // environment are now in the main values map and available
    // for immediate substitution
    protected static final String ENVSTART = "@ENV{'";
    protected static final String ENVSTOP = "'}";

    // Map of last values seen
    protected Map<String, String> values = new HashMap<>();

    // Get this once per jvm
    private static final String hostname;

    // Grab the hostname for @{HOST} replacement
    static {
        String tmpHostname;
        try {
            tmpHostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            logger.error("Error getting host name", e);
            tmpHostname = "localhost";
        }
        hostname = tmpHostname;
    }

    /**
     * Public default constructor
     */
    public ServiceConfigGuide() {
        initializeValues();
    }

    /**
     * Public constructor with dir and filename
     *
     * @param path the directory where config files are
     * @param file the name of te file in the directory
     */
    public ServiceConfigGuide(final String path, final String file) throws IOException {
        this(path + File.separator + file);
    }

    /**
     * Public default constructor with file name
     *
     * @param filename the name of the disk file
     */
    public ServiceConfigGuide(final String filename) throws IOException {
        this();
        try {
            readConfigData(filename);
        } catch (ConfigSyntaxException ex) {
            throw new IOException("Cannot parse configuration file " + ex.getMessage(), ex);
        }
    }

    /**
     * Public default constructor with InputStream
     *
     * @param is the InputStream
     */
    public ServiceConfigGuide(final InputStream is) throws IOException {
        this();
        try {
            readConfigData(is);
        } catch (ConfigSyntaxException ex) {
            throw new IOException("Cannot parse configuration file " + ex.getMessage(), ex);
        }
    }

    /**
     * Public default constructor with InputStream and name
     *
     * @param is the InputStream
     * @param name the name of the stream good for reporting errors
     */
    public ServiceConfigGuide(final InputStream is, final String name) throws IOException {
        this();
        try {
            readConfigData(is, name);
        } catch (ConfigSyntaxException ex) {
            logger.error("Caught ConfigSytaxException {}", ex.getMessage());
            throw new IOException("Cannot parse configuration file " + ex.getMessage(), ex);
        }
    }

    /**
     * Initialize the values map, which is used to replace stuff in the configs
     */
    protected void initializeValues() {
        this.values.clear();

        // TODO: see if we can stop adding all env variables and
        // system properties to the replace values

        // Add all the environment variables
        this.values.putAll(System.getenv());

        // Add all the system properties
        final Properties props = System.getProperties();
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
            final String key = (String) e.nextElement();
            logger.trace("Adding {} to replaceable properties", key);
            this.values.put(key, props.getProperty(key));
        }

        // used for substitution when reading cfg files
        this.values.put("CONFIG_DIR", StringUtils.join(ConfigUtil.getConfigDirs(), ","));
        this.values.put("PRJ_BASE", ConfigUtil.getProjectBase());
        this.values.put("PROJECT_BASE", ConfigUtil.getProjectBase());
        this.values.put("OUTPUT_ROOT", ConfigUtil.getOutputRoot());
        this.values.put("BIN_DIR", ConfigUtil.getBinDir());
        this.values.put("HOST", hostname);
        this.values.put("/", File.separator);
        this.values.put("TMPDIR", System.getProperty("java.io.tmpdir"));
        this.values.put("NULL", null);
        this.values.put("OS.NAME", System.getProperty("os.name").replace(' ', '_'));
        this.values.put("OS.VER", System.getProperty("os.version").replace(' ', '_'));
        this.values.put("OS.ARCH", System.getProperty("os.arch").replace(' ', '_'));
    }

    /**
     * Reads the configuration file specified in the argument and sets the mandatory parameters.
     */
    protected void readConfigData(final String filename) throws IOException, ConfigSyntaxException {
        readSingleConfigFile(filename);
    }

    public void readConfigData(final InputStream is) throws IOException, ConfigSyntaxException {
        readConfigData(is, "UNKNOWN");
    }


    protected void readConfigData(final InputStream is, final String filename) throws IOException, ConfigSyntaxException {
        final Reader r = new BufferedReader(new InputStreamReader(is));
        final StreamTokenizer in = new StreamTokenizer(r);
        int nextToken = StreamTokenizer.TT_WORD;
        String parmName;
        String sval;

        in.commentChar('#');
        in.wordChars(33, 33);
        in.wordChars(36, 47);
        in.wordChars(58, 64);
        in.wordChars(91, 96);
        in.wordChars(123, 65536);

        while (nextToken != StreamTokenizer.TT_EOF) {
            // Read three tokens at a time (X = Y)
            nextToken = in.nextToken();

            // Make sure the first token in the tuple is a word
            if (nextToken == StreamTokenizer.TT_EOF) {
                break;
            }
            if (nextToken == StreamTokenizer.TT_NUMBER) {
                throw new ConfigSyntaxException("Illegal token " + in.sval + ", missing quote on line " + in.lineno() + "?");
            }

            parmName = in.sval;

            nextToken = in.nextToken();
            this.operator = in.sval;

            nextToken = in.nextToken();
            if (nextToken == StreamTokenizer.TT_NUMBER) {
                sval = Long.toString((long) in.nval);
            } else {
                sval = in.sval;
            }

            if (sval == null) {
                // Problem is likely on previous line
                throw new ConfigSyntaxException("Illegal token " + parmName + ", missing space or value on line " + (in.lineno() - 1) + "?");
            }

            handleNewEntry(parmName, sval, this.operator, filename, in.lineno() - 1, false);
        }
        r.close();
        is.close();
    }

    protected void readSingleConfigFile(final String filename) throws IOException, ConfigSyntaxException {
        logger.debug("Reading config file {}", filename);
        final InputStream is = ConfigUtil.getConfigData(filename);
        readConfigData(is, filename);
    }

    /**
     * Handle a newly parsed or passed in entry. Substitutions are handled on both the LHS and RHS, then the values are
     * stored as a ConfigEntry in our local list and map. Only the last value in the map is available for substitutions. LHS
     * is analyzed before RHS and in L to R order.
     *
     * @param parmNameArg the LHS
     * @param svalArg the raw RHS
     * @param operatorArg the equation
     * @param filename the filename we are parsing for error reporting
     * @param lineno the line number we are currently reporting the error on
     * @param merge true when adding in from a merge
     * @return a new config entry with the expanded key and value
     * @throws IOException when the key or value is malformed
     */
    protected ConfigEntry handleNewEntry(final String parmNameArg, final String svalArg, final String operatorArg, final String filename,
            final int lineno, final boolean merge) throws IOException {
        final String parmName = handleReplacements(parmNameArg, filename, lineno);
        final String sval = handleReplacements(svalArg, filename, lineno);

        // Create a config entry from this
        final ConfigEntry anEntry = new ConfigEntry(parmName, sval);

        if ("!=".equals(operatorArg)) {
            if ("*".equals(sval)) {
                removeAllEntries(parmName);
                this.values.remove(parmName);
            } else {
                removeEntry(anEntry);
                if (sval.equals(this.values.get(parmName))) {
                    this.values.remove(parmName);
                }
            }
            this.removeParameters.add(anEntry);
        } else {
            // Save the entry in the list
            if (merge) {
                this.serviceParameters.add(0, anEntry);
            } else {
                this.serviceParameters.add(anEntry);
            }

            // Save this pair in the map
            this.values.put(parmName, sval);
        }

        if ("IMPORT_FILE".equals(parmName) || "OPT_IMPORT_FILE".equals(parmName)) {
            final List<String> fileFlavorList = new ArrayList<>();
            // Add the base file and then add all the flavor versions
            fileFlavorList.add(sval);
            final String[] fileFlavors = ConfigUtil.addFlavors(sval);
            if (ArrayUtils.isNotEmpty(fileFlavors)) {
                fileFlavorList.addAll(Arrays.asList(fileFlavors));
            }
            logger.debug("ServiceConfigGuide::handleNewEntry -- FileFlavorList = {}", fileFlavorList);

            // loop through the files and attempt to read/merger the configurations.
            for (int i = 0; i < fileFlavorList.size(); i++) {
                final String fileFlavor = fileFlavorList.get(i);
                // recursion alert: This could lead to getFile being called
                try {
                    readConfigData(ConfigUtil.getConfigStream(fileFlavor), fileFlavor);
                } catch (ConfigSyntaxException e) {
                    // whether opt or not, syntax errors are a problem
                    throw new IOException(parmName + " = " + sval + " from " + filename + " failed " + e.getMessage(), e);
                } catch (IOException e) {
                    // Throw exception if it is an IMPORT_FILE and the base file is not found
                    if ("IMPORT_FILE".equals(parmName) && i == 0) {
                        String importFileName = Paths.get(svalArg).getFileName().toString();
                        throw new IOException("In " + filename + ", cannot find IMPORT_FILE: " + sval
                                + " on the specified path. Make sure IMPORT_FILE (" + importFileName + ") exists, and the file path is correct.",
                                e);
                    }
                }
            }
            return anEntry;
        } else if ("CREATE_DIRECTORY".equals(parmName) && !createDirectory(sval)) {
            logger.warn("{}: Cannot create directory {}", filename, sval);
        } else if ("CREATE_FILE".equals(parmName) && !createFile(sval)) {
            logger.warn("{}: Cannot create file {}", filename, sval);
        }

        return anEntry;
    }

    /**
     * Handle all the possible replacements in a string value
     *
     * @param svalArg the raw value
     * @param filename the filename we are parsing for error reporting
     * @param lineno the line number we are currently reporting the error on
     * @return the expanded value with all legal @{..} values replaced
     * @throws IOException when the value is malformed
     */
    protected String handleReplacements(final String svalArg, final String filename, final int lineno) throws IOException {
        String sval = svalArg;
        int startpos = 0;
        while (sval != null && sval.indexOf(VSTART, startpos) > -1) {
            final int ndx = sval.indexOf(VSTART, startpos);
            final int edx = sval.indexOf(VEND, ndx + VSTART.length());
            if (ndx == -1 && ndx >= edx) {
                throw new IOException("Problem parsing line " + lineno + " " + sval);
            }
            final String tok = sval.substring(ndx + VSTART.length(), edx);
            logger.debug("Replacement token is {}", tok);
            final String mapval = this.values.get(tok);
            if (mapval != null) {
                sval = sval.substring(0, ndx) + mapval + sval.substring(edx + VEND.length());
            } else {
                logger.warn("Did not find replacement for '{}' in file {} at line {}", tok, filename, lineno);
                startpos = edx + VEND.length();
            }
        }

        // This is obsolete
        if (sval != null && sval.contains(ENVSTART)) {
            sval = substituteEnvProps(sval, filename, lineno);
        }

        // Do unicode stuff
        if (sval != null && (sval.contains("\\u") || sval.contains("\\U"))) {
            sval = substituteUtfChars(sval, filename, lineno);
        }

        // This is obsolete
        if (sval != null && sval.equals(NULL_VALUE)) {
            sval = null;
            logger.debug("Using {} is deprecated, please just use {}NULL{}", NULL_VALUE, VSTART, VEND);
        }
        return sval;
    }

    /**
     * Substitute any java unicode character values: \\uxxxx
     *
     * @param s the string to process
     * @param filename the name of the file we are in for error reporting
     * @param lnum the current line number for error reporting
     * @return string with character values replaced
     */
    protected String substituteUtfChars(final String s, final String filename, final int lnum) throws IOException {
        final int slen = s.length();
        final StringBuilder sb = new StringBuilder(slen);
        for (int i = 0; i < slen; i++) {
            if (s.charAt(i) != '\\') {
                sb.append(s.charAt(i));
            } else if ((i + 4) < slen && (s.charAt(i + 1) == 'u' || s.charAt(i + 1) == 'U')) {
                int epos = i + 2;
                final int max = (s.charAt(epos) == '1' || s.charAt(epos) == '0') ? (i + 7) : (i + 6);
                while (epos < slen
                        && epos < max
                        && ((s.charAt(epos) >= '0' && s.charAt(epos) <= '9') || (s.charAt(epos) >= 'A' && s.charAt(epos) <= 'F')
                                || (s.charAt(epos) >= 'a' && s
                                        .charAt(epos) <= 'f'))) {
                    epos++;
                }
                if (epos <= slen) {
                    try {
                        final int digit = Integer.parseInt(s.substring(i + 2, epos), 16);
                        sb.appendCodePoint(digit);
                        i = epos - 1;
                    } catch (RuntimeException ex) {
                        throw new IOException("Unable to convert characters in " + s + ", from filename=" + filename + " line " + lnum, ex);
                    }
                }
            } else {
                sb.append(s.charAt(i));
            }
        }

        return sb.toString();
    }

    /**
     * Substitute any referenced env properties with their values. Look for @ENV{'foo'} and replace foo with
     * System.getProperty("foo") or System.getenv("foo") in that order.
     *
     * @param str the string to process
     * @param filename the name of the file we are in for error reporting
     * @param lnum the current line number for error reporting
     * @return string with env values replaced
     */
    protected String substituteEnvProps(final String str, final String filename, final int lnum) throws IOException {
        int lastPos = -1;
        int thisPos = 0;
        int count = 0;

        logger.debug("{}{} style substitution is deprecated. Please just use {}yourvalue{}", ENVSTART, ENVSTOP, VSTART, VEND);

        String currentStr = str;
        while ((thisPos = currentStr.indexOf(ENVSTART, thisPos)) > lastPos) {
            final int start = thisPos + ENVSTART.length();
            final int stop = currentStr.indexOf(ENVSTOP, thisPos);
            count++;
            // Pull out the env name they specified
            if (stop > start) {
                final String envName = currentStr.substring(start, stop);
                String envVal = System.getProperty(envName);
                if (envVal == null) {
                    envVal = System.getenv(envName);
                }
                // We got a replacement, do the subst
                if (envVal != null) {
                    currentStr = currentStr.substring(0, thisPos) + // before
                            envVal + // replacement value
                            currentStr.substring(stop + ENVSTOP.length()); // tail
                    logger.debug("Replaced {} with {} at {}: {}", envName, envVal, filename, lnum);
                } else {
                    logger.debug("No env value for {} at {}: {}", envName, filename, lnum);
                }
            } else {
                throw new IOException("Runaway string on line ->" + currentStr + "<- at " + filename + ": " + lnum);
            }

            lastPos = thisPos;
        }
        logger.debug("Found {} env vars to subst --> {}", count, currentStr);
        return currentStr;
    }

    /**
     * Create a directory as specified by the config driver
     */
    protected boolean createDirectory(final String sval) {
        final String fixedSval = sval.replace('\\', '/');
        logger.debug("Trying to create dir {}", fixedSval);
        final File d = new File(fixedSval);
        if (!d.exists() && !d.mkdirs()) {
            logger.debug("Failed to create directory {}", fixedSval);
            return false;
        }
        return true;
    }

    /**
     * Create a file as specified by the config driver
     */
    protected boolean createFile(final String sval) {

        final String fixedSval = sval.replace('\\', '/');
        logger.debug("Trying to create file {}", fixedSval);
        final File d = new File(fixedSval);
        FileWriter newFile = null;
        if (!d.exists()) {
            try {
                // Ensure the directory exists to hold the file
                final File parent = new File(new File(d.getCanonicalPath()).getParent());
                if (!parent.exists() && !createDirectory(parent.toString())) {
                    logger.debug("Failed to create parent directory for {}", fixedSval);
                    return false;
                }
                // Create the file in the directory
                newFile = new FileWriter(d);
            } catch (IOException e) {
                logger.debug("Failed to create file {}", fixedSval, e);
                return false;
            } finally {
                if (newFile != null) {
                    try {
                        newFile.close();
                    } catch (IOException ioe) {
                        logger.debug("Error closing file", ioe);
                    }
                }
            }
        }
        return true;
    }

    /**
     * Get the names of all entries for this config This set is not backed by the configuration and any changes to it are
     * not reflected in the configuration.
     */
    @Override
    public Set<String> entryKeys() {
        final Set<String> set = new HashSet<>();
        for (final ConfigEntry curEntry : this.serviceParameters) {
            set.add(curEntry.getKey());
        }
        return set;
    }

    /**
     * Get all the entries for this config This is a copy and changes to it are not reflected in the configuration
     */
    @Override
    public List<ConfigEntry> getEntries() {
        return new ArrayList<>(this.serviceParameters);
    }

    /**
     * Remove entries, those with operators of '!=' are stored and can be retrieved for replay during merge. This method is
     * not part of the Configurator interface.
     */
    protected List<ConfigEntry> getRemoveEntries() {
        return new ArrayList<>(this.removeParameters);
    }

    /**
     * Add an entry to this config
     *
     * @param key the name of the entry
     * @param value the value
     * @return the new entry or null if it fails
     */
    @Override
    public ConfigEntry addEntry(final String key, final String value) {
        ConfigEntry entry = null;
        try {
            entry = handleNewEntry(key, value, "=", "<user>", 1, false);
        } catch (IOException ex) {
            logger.error("Could not add entry for {}", key, ex);
        }
        return entry;
    }

    /**
     * Add a list of entries for the same key
     *
     * @param key the name of the entry
     * @param values the values
     * @return the new entries or null if it fails
     */
    @Override
    public List<ConfigEntry> addEntries(final String key, final List<String> values) {
        final List<ConfigEntry> list = new ArrayList<>();
        try {
            int i = 1;
            for (final String value : values) {
                final ConfigEntry entry = handleNewEntry(key, value, "=", "<user>", i++, false);
                list.add(entry);
            }
        } catch (IOException ex) {
            logger.error("Error adding entries for {}", key, ex);
        }
        return list;
    }

    /**
     * Remove all entries by the given name
     *
     * @param key the name of the entry or entries
     * @param value the value
     */
    @Override
    public void removeEntry(final String key, final String value) {
        try {
            handleNewEntry(key, value, "!=", "<user>", 1, false);
        } catch (IOException ex) {
            logger.warn("Cannot remove entry", ex);
        }
    }

    /**
     * Remove an entry from the list of parameters matching the ConfigEntry argument passed in.
     *
     * @param anEntry the entry to remove
     */
    public void removeEntry(final ConfigEntry anEntry) {
        // NB: enhanced for loop does not support remove
        for (final Iterator<ConfigEntry> i = this.serviceParameters.iterator(); i.hasNext();) {
            final ConfigEntry curEntry = i.next();
            if (anEntry.getKey().equals(curEntry.getKey())
                    && ((anEntry.getValue() == null && curEntry.getValue() == null) || (anEntry.getValue() != null && anEntry.getValue().equals(
                            curEntry.getValue())))) {
                logger.debug("Removing {} = {}", curEntry.getKey(), curEntry.getValue());
                i.remove();
            }
        }
    }

    /**
     * Return a list containing all the parameter values matching the key argument passed in.
     *
     * @param theParameter the key to match
     * @param defaultString value for list when no matches are found
     * @return the list with all matching entries or the default value supplied
     */
    @Override
    public List<String> findEntries(final String theParameter, final String defaultString) {
        final List<String> result = findEntries(theParameter);
        if (result.isEmpty()) {
            result.add(defaultString);
        }
        return result;
    }

    /**
     * Return a list containing all the parameter values matching the key argument passed in
     *
     * @param theParameter the key to match
     * @return list with all matching entries, or empty list if none
     */
    @Override
    public List<String> findEntries(final String theParameter) {
        final List<String> matchingEntries = new ArrayList<>();

        for (final ConfigEntry curEntry : this.serviceParameters) {
            if (theParameter.equals(curEntry.getKey())) {
                matchingEntries.add(curEntry.getValue());
            }
        }
        return matchingEntries;
    }

    /**
     * Remove all entries from the list of parameters matching the String argument passed in.
     *
     * @param theParameter key name to match, all matching will be removed
     */
    public void removeAllEntries(final String theParameter) {
        // NB: enhanced for loop does not support remove
        for (final Iterator<ConfigEntry> i = this.serviceParameters.iterator(); i.hasNext();) {
            final ConfigEntry curEntry = i.next();
            if (theParameter.equals(curEntry.getKey())) {
                logger.debug("Removing {} = {}", curEntry.getKey(), curEntry.getValue());
                i.remove();
            }
        }
    }

    /**
     * Return a set with all parameter values as members
     *
     * @param theParameter key value to match
     * @return set of all entries found or empty set if none
     */
    @Override
    public Set<String> findEntriesAsSet(final String theParameter) {

        final Set<String> matchingEntries = new HashSet<>();

        for (final ConfigEntry curEntry : this.serviceParameters) {
            if (theParameter.equals(curEntry.getKey())) {
                matchingEntries.add(curEntry.getValue());
            }
        }
        return matchingEntries;
    }

    /**
     * Find entries beginning with the specified string
     *
     * @param theParameter key to match with a startsWith
     * @return list of entries matching specified value or empty list if none
     */
    @Override
    public List<ConfigEntry> findStringMatchEntries(final String theParameter) {

        final List<ConfigEntry> matchingEntries = new ArrayList<>();

        for (final ConfigEntry curEntry : this.serviceParameters) {
            if (curEntry.getKey().startsWith(theParameter)) {
                matchingEntries.add(curEntry);
            }
        }
        return matchingEntries;
    }

    /**
     * Find entries beginning with the specified string and put them into a list with the specified part of the name
     * stripped off like #findStringMatchMap
     *
     * @param theParameter key to match with a startsWith
     * @return list of ConfigEntry
     */
    @Override
    public List<ConfigEntry> findStringMatchList(final String theParameter) {
        final List<ConfigEntry> list = findStringMatchEntries(theParameter);
        for (final ConfigEntry entry : list) {
            entry.setKey(entry.getKey().substring(theParameter.length()));
        }
        return list;
    }

    /**
     * Find entries beginning with the specified string and return a hash keyed on the remainder of the string with the
     * value of the config line as the value of the hash
     *
     * @param theParameter the key to look for in the config file
     */
    @Override
    public Map<String, String> findStringMatchMap(final String theParameter) {
        return findStringMatchMap(theParameter, false);
    }

    /**
     * Find entries beginning with the specified string and return a hash keyed on the remainder of the string with the
     * value of the config line as the value of the hash
     *
     * <pre>
     * {@code
     * Example config entries
     *    FOO_ONE: AAA
     *    FOO_TWO: BBB
     * Calling findStringMatchMap("FOO_",true)
     * will yield a map with
     *     ONE -> AAA
     *     TWO -> BBB
     * }
     * </pre>
     *
     * @param theParameter the key to look for in the config file
     * @param preserveCase if false all keys will be upcased
     * @return map where key is remainder after match and value is the config value, or an empty map if none found
     */
    @Override
    public Map<String, String> findStringMatchMap(final String theParameter, final boolean preserveCase) {
        return findStringMatchMap(theParameter, preserveCase, false);
    }

    /**
     * Find entries beginning with the specified string and return a hash keyed on the remainder of the string with the
     * value of the config line as the value of the hash
     *
     * <pre>
     * {@code
     * Example config entries
     *    FOO_ONE: AAA
     *    FOO_TWO: BBB
     * Calling findStringMatchMap("FOO_",true)
     * will yield a map with
     *     ONE -> AAA
     *     TWO -> BBB
     * }
     * </pre>
     *
     * @param theParameter the key to look for in the config file
     * @param preserveCase if false all keys will be upcased
     * @param preserveOrder if true key ordering is preserved
     * @return map where key is remainder after match and value is the config value, or an empty map if none found
     */
    @Override
    public Map<String, String> findStringMatchMap(@Nullable final String theParameter, final boolean preserveCase, final boolean preserveOrder) {
        if (theParameter == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> theHash = preserveOrder ? new LinkedHashMap<>() : new HashMap<>();
        final List<ConfigEntry> parameters = this.findStringMatchEntries(theParameter);

        for (final ConfigEntry el : parameters) {
            String key = el.getKey();
            key = key.substring(theParameter.length());
            if (!preserveCase) {
                key = key.toUpperCase(Locale.getDefault());
            }
            theHash.put(key, el.getValue());
        }
        return theHash;
    }

    /**
     * Find entries beginning with the specified string and return a hash keyed on the remainder of the string with the
     * value of the config line as the value of the hash Multiple values for the same hash are allowed and returned as a
     * Set.
     *
     * <pre>
     * {@code
     * Example config entries
     *    FOO_ONE: AAA
     *    FOO_TWO: BBB
     *    FOO_TWO: CCC
     * Calling findStringMatchMap("FOO_",true)
     * will yield a map with Sets
     *     ONE -> {AAA}
     *     TWO -> {BBB,CCC}
     * }
     * </pre>
     *
     * @param param the key to look for in the config file
     * @return map where key is remainder after match and value is a Set of all found config values, or an empty map if none
     *         found
     */
    @Override
    public Map<String, Set<String>> findStringMatchMultiMap(@Nullable final String param) {
        if (param == null) {
            return Map.of();
        }

        return findStringMatchMultiMap(param, false);
    }

    /**
     * Find entries beginning with the specified string and return a hash keyed on the remainder of the string with the
     * value of the config line as the value of the hash Multiple values for the same hash are allowed and returned as a
     * Set.
     *
     * <pre>
     * {@code
     * Example config entries
     *    FOO_ONE: AAA
     *    FOO_TWO: BBB
     *    FOO_TWO: CCC
     * Calling findStringMatchMap("FOO_",true)
     * will yield a map with Sets
     *     ONE -> {AAA}
     *     TWO -> {BBB,CCC}
     * }
     * </pre>
     *
     * @param param the key to look for in the config file
     * @param preserveOrder ordering of keys is preserved
     * @return map where key is remainder after match and value is a Set of all found config values, or an empty map if none
     *         found
     */
    @Override
    public Map<String, Set<String>> findStringMatchMultiMap(@Nullable final String param, final boolean preserveOrder) {

        if (param == null) {
            return Map.of();
        }

        final Map<String, Set<String>> theHash = preserveOrder ? new LinkedHashMap<>() : new HashMap<>();
        final List<ConfigEntry> parameters = this.findStringMatchEntries(param);

        for (final ConfigEntry el : parameters) {
            final String key = el.getKey().substring(param.length()).toUpperCase(Locale.getDefault());

            if (theHash.containsKey(key)) {
                theHash.get(key).add(el.getValue());
            } else {
                final Set<String> values = preserveOrder ? new LinkedHashSet<>() : new HashSet<>();
                values.add(el.getValue());
                theHash.put(key, values);
            }
        }
        return theHash;

    }

    /**
     * Return the first string entry matching the key parameter
     *
     * @param theParameter key to match
     * @return the first matching value
     * @throws IllegalArgumentException if no non-blank value is found
     */
    @Override
    public String findRequiredStringEntry(final String theParameter) {
        String value = findStringEntry(theParameter, null);
        Validate.notBlank(value, "Missing required parameter [%s]", theParameter);
        return value;
    }

    /**
     * Return the first string entry matching the key parameter or the default if no match is found
     *
     * @param theParameter the key to match
     * @param dflt string to use when no matches are found
     * @return the first matching entry of the default if none found
     */
    @Override
    public String findStringEntry(final String theParameter, @Nullable final String dflt) {
        final List<String> matchingEntries = findEntries(theParameter);
        for (final String entry : matchingEntries) {
            if (entry != null) {
                return entry;
            }
        }
        return dflt;
    }

    /**
     * Return the first string entry matching the key parameter or null if no match is found
     *
     * @param theParameter key to match
     * @return the first matching value or null if none
     */
    @Override
    public String findStringEntry(final String theParameter) {
        return findStringEntry(theParameter, null);
    }

    /**
     * Return the last (newest) string entry matching the key parameter or an empty string if no match is found
     *
     * @param theParameter the key to match
     * @return the last matching value or empty string if none found
     */
    @Override
    public String findLastStringEntry(final String theParameter) {
        String result = "";
        for (final ConfigEntry curEntry : this.serviceParameters) {
            if (theParameter.equals(curEntry.getKey())) {
                result = curEntry.getValue();
            }
        }
        return result;
    }

    /**
     * Return a long from a string entry representing either an int, a long, a double, with or without a final letter
     * designation such as "m" or "M" for megabytes, "g" or "G" for gigabytes, etc. Legal designations are bBkKmMgGTt or
     * just a number.
     *
     * @param theParameter the config entry name
     * @param dflt the default value when nothing found in config
     * @return the long value of the size parameter
     */
    @Override
    public long findSizeEntry(final String theParameter, final long dflt) {
        final List<String> matchingEntries = findEntries(theParameter);
        if (!matchingEntries.isEmpty()) {
            long val = dflt;
            final String s = matchingEntries.get(0);
            final char c = Character.toUpperCase(s.charAt(s.length() - 1));
            final String ss = s.substring(0, s.length() - 1);
            boolean broken = false;
            switch (c) {
                case 'T':
                    val = Long.parseLong(ss) * 1024 * 1024 * 1024 * 1024;
                    break;
                case 'G':
                    val = Long.parseLong(ss) * 1024 * 1024 * 1024;
                    break;
                case 'M':
                    val = Long.parseLong(ss) * 1024 * 1024;
                    break;
                case 'K':
                    val = Long.parseLong(ss) * 1024;
                    break;
                case 'B':
                    val = Long.parseLong(ss);
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    val = Long.parseLong(s);
                    break;
                default:
                    broken = true;
            }

            if (!broken) {
                return val;
            }
            return dflt;
        }
        return dflt;
    }

    /**
     * Return the string canonical file name of a matching entry
     *
     * @param theParameter the key to match
     * @param dflt the string to use as a default when no matches are found
     * @return the first matching value run through File.getCanonicalPath
     */
    @Override
    public String findCanonicalFileNameEntry(final String theParameter, final String dflt) {
        final String fn = findStringEntry(theParameter, dflt);
        if (fn != null && fn.length() > 0) {
            try {
                return new File(fn).getCanonicalPath();
            } catch (IOException ex) {
                logger.error("Cannot compute canonical path on {}", fn, ex);
            }
        }
        return fn;
    }

    /**
     * Return an int of the first entry matching the key parameter or the default if no match is found
     *
     * @param theParameter the key to match
     * @param dflt the int to use when no matches are found
     * @return the first matching value or the default when none found
     */
    @Override
    public int findIntEntry(final String theParameter, final int dflt) {
        final List<String> matchingEntries = findEntries(theParameter);

        if (!matchingEntries.isEmpty()) {
            try {
                return Integer.parseInt(matchingEntries.get(0));
            } catch (NumberFormatException e) {
                logger.warn("{} is non-numeric returning default value: {}", theParameter, dflt);
            }
        }
        return dflt;
    }

    /**
     * Return a long of the first entry matching the key parameter or the default if no match is found
     *
     * @param theParameter the key to match
     * @param dflt the value to use when no matches are found
     * @return the first matching value or the default when none found
     */
    @Override
    public long findLongEntry(final String theParameter, final long dflt) {
        final List<String> matchingEntries = findEntries(theParameter);

        if (!matchingEntries.isEmpty()) {
            try {
                return Long.parseLong(matchingEntries.get(0));
            } catch (NumberFormatException e) {
                logger.warn("{} is non-numeric returning default value: {}", theParameter, dflt);
            }
        }
        return dflt;
    }

    /**
     * Return a double of the first entry matching the key parameter or the default if no match is found
     *
     * @param theParameter the key to match
     * @param dflt the value to use when no matches are found
     * @return the first matching value or the default when none found
     */
    @Override
    public double findDoubleEntry(final String theParameter, final double dflt) {
        final List<String> matchingEntries = findEntries(theParameter);

        if (!matchingEntries.isEmpty()) {
            try {
                return Double.parseDouble(matchingEntries.get(0));
            } catch (NumberFormatException e) {
                logger.warn("{} is non-numeric returning default value: {}", theParameter, dflt);
            }
        }
        return dflt;
    }

    /**
     * Return boolean of the first entry matching the key parameter or the default if no match is found
     *
     * @param theParameter the key to match
     * @param dflt the value to use when no matches are found
     * @return the first matching value or the default when none found
     */
    @Override
    public boolean findBooleanEntry(final String theParameter, final boolean dflt) {
        final List<String> matchingEntries = findEntries(theParameter);

        if (!matchingEntries.isEmpty()) {
            String el = matchingEntries.get(0);
            el = el.toUpperCase(Locale.getDefault());
            if (el.startsWith("F")) {
                return false;
            } else if (el.startsWith("T")) {
                return true;
            }
        }
        return dflt;
    }

    /**
     * Return boolean of the first entry matching the key parameter or the default if no match is found
     *
     * @param theParameter the key to match
     * @param dflt the value to use when no matches are found
     * @return the first matching value or the default when none found
     */
    @Override
    public boolean findBooleanEntry(final String theParameter, final String dflt) {
        return findBooleanEntry(theParameter, Boolean.parseBoolean(dflt));
    }

    /**
     * Get the value of a parameter that is purported to be numeric
     *
     * @param name the name of the parameter
     */
    protected int getNumericParameter(final String name) {
        final String val = this.values.get(name);
        int i = -1;
        if (val != null) {
            try {
                i = Integer.parseInt(val);
            } catch (NumberFormatException ex) {
                logger.warn("{} is non-numeric: {}", name, val);
            }
        }
        return i;
    }

    public boolean debug() {
        return "TRUE".equalsIgnoreCase(this.values.get("DEBUG"));
    }

    /**
     * Merge in a new configuration set with this one. New things are supposed to override older things in the sense of
     * findStringEntry which only picks the top of the list, the new things should get added to the top. If the merged in
     * Configurator contains remove entries (operator of '!=') then it only applies to entries in this instance, not in
     * "other". This is slightly different than when a config is read in directly, but without that there would be no way to
     * remove entries from a super-config and continue to supply entries here and still be able to use the wildcard remove
     * (value of '*') The order in the merged config file is important. Any '!= "*"' operations must precede the new value
     * being supplied since normal remove operations take place in each config before the merge.
     *
     * @param other the new entries to merge in
     */
    @Override
    public void merge(final Configurator other) throws IOException {
        int i = 1;

        // First handle the remove entries from "other"
        if (other instanceof ServiceConfigGuide) {
            for (final ConfigEntry entry : ((ServiceConfigGuide) other).getRemoveEntries()) {
                handleNewEntry(entry.getKey(), entry.getValue(), "!=", "<merge>", i++, true);
            }
        }

        // Add in new entries from "other" at the top of the list
        for (final ConfigEntry entry : other.getEntries()) {
            handleNewEntry(entry.getKey(), entry.getValue(), "=", "<merge>", i++, true);
        }
    }

    /**
     * Public main used to verify config file construction off-line
     */
    public static void main(final String[] args) {
        if (args.length < 1) {
            logger.error("usage: java ServiceConfigGuide configfile");
            return;
        }

        for (String arg : args) {
            try {
                final ServiceConfigGuide sc = new ServiceConfigGuide(arg);
                logger.info("Config File:{} ", arg);
                for (int i = 0; i < sc.serviceParameters.size(); i++) {
                    final ConfigEntry c = sc.serviceParameters.get(i);
                    logger.info("{}: {}", c.getKey(), c.getValue());
                }
                logger.info("---");
            } catch (IOException e) {
                logger.info("Cannot process {}:{}", arg, e.getLocalizedMessage());
            }
        }
    }
}
