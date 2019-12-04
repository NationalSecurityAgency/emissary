package emissary.core;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides metadata renaming and remapping based on values in its configuration file. There are a set of
 * exact match rename values, and a set of regex that can be applied to get the new name. Provision is made to store one
 * of these dictionaries in the global Namespace and retrieve it if desired, but this class is NOT a singleton.
 *
 * For performance reasons, a second exact match map is kept for things that matched due to a regex. This map is size
 * limited and is handled as an LRU.
 *
 * THe regex list and the name map are not synchronized. Once the class is configured they do not change. If your
 * implementation allows them to change, then you should carefully consider synchronization issues.
 */
public class MetadataDictionary {
    /**
     * The default name by which we register into the namespace
     */
    public static final String DEFAULT_NAMESPACE_NAME = "MetadataDictionary";

    /**
     * Our logger
     */
    protected final Logger logger = LoggerFactory.getLogger(MetadataDictionary.class);

    /**
     * The name used by this pool
     */
    protected String namespaceName = DEFAULT_NAMESPACE_NAME;

    /**
     * The map of exact match renames
     */
    protected Map<String, String> nameMap;

    /**
     * The map of regex to run when requested
     */
    protected Map<Pattern, String> regexMap;

    /**
     * The regex match cache lru
     */
    protected Map<String, String> regexCache;

    /**
     * Create one and register with the default name
     */
    public MetadataDictionary() {
        reconfigure(null);
    }

    /**
     * Create one and register with the specified name
     * 
     * @param ns the namespace name
     */
    public MetadataDictionary(final String ns) {
        this.namespaceName = ns;
        reconfigure(null);
    }

    /**
     * Create one and register with the specified name using the config stream
     * 
     * @param ns the namespace name
     * @param conf the config stream to use
     */
    public MetadataDictionary(final String ns, final Configurator conf) {
        this.namespaceName = ns;
        reconfigure(conf);
    }

    /**
     * Setup from configuration file or stream Items used here are
     * <ul>
     * <li>RENAME_* - read into a map for exact match renaming</li>
     * <li>REGEX - regex patterns that can drive renaming</li>
     * <li>CACHE_SIZE - size for LRU regex cache</li>
     * </ul>
     * 
     * @param confArg the config stream to use or null for default
     */
    @SuppressWarnings("unchecked")
    protected void reconfigure(final Configurator confArg) {
        try {
            final Configurator conf;
            if (confArg == null) {
                conf = ConfigUtil.getConfigInfo(MetadataDictionary.class);
            } else {
                conf = confArg;
            }

            this.nameMap = conf.findStringMatchMap("RENAME_", Configurator.PRESERVE_CASE);

            final int cacheSize = conf.findIntEntry("CACHE_SIZE", 500);
            this.regexCache = new ConcurrentHashMap<String, String>(new LRUMap(cacheSize));
            this.logger.debug("LRU cache configured with size {}", cacheSize);

            final Map<String, String> list = conf.findStringMatchMap("REGEX_", Configurator.PRESERVE_CASE);

            this.regexMap = new HashMap<Pattern, String>();

            for (final Map.Entry<String, String> entry : list.entrySet()) {
                try {
                    this.regexMap.put(Pattern.compile(entry.getKey()), entry.getValue());
                } catch (Exception pex) {
                    this.logger.warn("Pattern '{}' could not compile", entry.getKey(), pex);
                }
            }
        } catch (IOException ex) {
            this.logger.warn("Cannot read config file", ex);
        } finally {
            if (this.nameMap == null) {
                this.nameMap = Collections.emptyMap();
            }

            if (this.regexMap == null) {
                this.regexMap = Collections.emptyMap();
            }

            if (this.regexCache == null) {
                this.regexCache = new LRUMap(500);
            }
        }
    }

    /**
     * Bind this object into the namespace
     */
    protected void bind() {
        Namespace.bind(this.namespaceName, this);
    }

    /**
     * Get the namespace name for this dictionary
     */
    public String getDictionaryName() {
        return this.namespaceName;
    }

    /**
     * Unbind this object
     */
    protected void unbind() {
        Namespace.unbind(this.namespaceName);
    }

    /**
     * Factory method to initialize and bind a new default named dictionary
     */
    public static MetadataDictionary initialize() {
        return initialize(DEFAULT_NAMESPACE_NAME);
    }

    /**
     * Factory method to initialize and bind a new dictionary
     * 
     * @param ns the namespace anem
     */
    public static MetadataDictionary initialize(final String ns) {
        final MetadataDictionary m = new MetadataDictionary(ns);
        m.bind();
        return m;
    }

    /**
     * Factory method to initialize and bind a new dictionary using a config object
     * 
     * @param ns the namespace anem
     * @param conf the config stream to use
     */
    public static MetadataDictionary initialize(final String ns, final Configurator conf) {
        final MetadataDictionary m = new MetadataDictionary(ns, conf);
        m.bind();
        return m;
    }

    /**
     * Get the dictionary object from the namespace using the default name
     */
    public static MetadataDictionary lookup() throws NamespaceException {
        return (MetadataDictionary) Namespace.lookup(DEFAULT_NAMESPACE_NAME);
    }

    /**
     * Get the dictionary object from the namespace using the specified name
     * 
     * @param ns the namespace name
     */
    public static MetadataDictionary lookup(final String ns) throws NamespaceException {
        return (MetadataDictionary) Namespace.lookup(ns);
    }

    /**
     * Nice string for namespace printouts
     */
    @Override
    public String toString() {
        return ("Metadata Dictionary names/regexes " + this.nameMap.size() + "/" + this.regexMap.size());
    }

    /**
     * Find a new name for the specified metadata element just using exact matches from the nameMap
     * 
     * @param m the metadata element name
     * @return the new name or at least the original name of no remapping
     */
    public String rename(final String m) {
        final String r = this.nameMap.get(m);
        if (r == null) {
            return m;
        }
        return r;
    }

    /**
     * Return a regex renaming of the metadata element name
     * 
     * @param m the metadata element name
     * @return the new name as supplied by the first matching regexp or the original name if nothing matches
     */
    public String regex(final String m) {
        String r = this.regexCache.get(m);
        if (r != null) {
            this.logger.trace("Found cache match for {} --> {}", m, r);
            return r;
        }

        for (final Pattern p : this.regexMap.keySet()) {
            try {
                final Matcher matcher = p.matcher(m);
                if (matcher.matches()) {
                    final String repl = this.regexMap.get(p);
                    if (repl != null) {
                        r = matcher.replaceAll(repl);
                        this.logger.trace("Found regex match for {} --> {}", m, r);
                        this.regexCache.put(m, r);
                        return r;
                    }
                    this.logger.debug("Matching pattern {} has no replacement string", p.pattern());
                }
            } catch (Exception pex) {
                this.logger.debug("Pattern matching or replacement problem", pex);
            }

            // Cache misses too so we don't have to try every keySet again
            this.regexCache.put(m, m);

        }
        return m;
    }

    /**
     * Remap a metadata element
     */
    public String map(final CharSequence cs) {
        if (cs instanceof String) {
            return map((String) cs);
        } else {
            return map(cs.toString());
        }
    }

    /**
     * Remap a metadata element or return the original name
     * 
     * @param m the metadata element name
     * @return the new name if there is one, or the old name if not
     */
    public String map(final String m) {
        // We don't call rename(s) here because we want
        // to check the null return rather than have to
        // do a string compare to see if a mapping was done
        String r = this.nameMap.get(m);
        if (r == null) {
            r = regex(m);
        }
        if (r == null) {
            r = m;
        }
        return r;
    }
}
