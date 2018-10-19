package emissary.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Help determine type of data from various data file mappings The name of the file gives some context to the mappings
 * it provides.
 */
public class TypeEngine {
    // Private logger
    private static final Logger logger = LoggerFactory.getLogger(TypeEngine.class);

    // Map of name to Configurator
    protected Map<String, Configurator> contextMapping = new HashMap<String, Configurator>();

    // Map of name to Map for extra mappings
    protected Map<String, Map<String, String>> extraMapping = new HashMap<String, Map<String, String>>();

    /**
     * Configure from a Configurator of the delegating class
     */
    public TypeEngine(Configurator configG) {
        List<String> l = configG.findEntries("TYPE_ENGINE_FILE");
        if (l.size() > 0) {
            configure(l);
        }
    }

    /**
     * Configure from a list of config files
     */
    public TypeEngine(List<String> configFiles) {
        configure(configFiles);
    }

    /**
     * Just build an empty one
     */
    public TypeEngine() {}

    /**
     * Configure it
     */
    public void configure(List<String> configFiles) {

        if (configFiles == null) {
            logger.info("No files specified for type engine");
            return;
        }

        for (String name : configFiles) {
            try {
                Configurator c = ConfigUtil.getConfigInfo(name);
                String engineName = c.findStringEntry("ENGINE_TYPE", name);
                logger.debug("TypeEngine loaded " + name + " as " + engineName);
                contextMapping.put(engineName, c);
            } catch (IOException e) {
                logger.error("TypeEngine unable to read " + name, e);
            }
        }
    }

    /**
     * Look up label in specified engine
     * 
     * @param engine name of the engine to use
     * @param label LHS part of equation to lookup
     * @return RHS part of mapping or null if none found
     */
    public String getForm(String engine, String label) {

        String ret = null;

        // check params
        if (engine == null || label == null) {
            logger.debug("Cannot process null arg engine=" + engine + ", label=" + label);
            return ret;
        }

        // Look up an override mapping
        Map<String, String> extra = extraMapping.get(engine);
        if (extra != null) {
            ret = extra.get(label.toUpperCase());
        }

        // Grab the specified engine and do the default lookup
        if (ret == null) {
            Configurator c = contextMapping.get(engine);
            if (c != null) {
                ret = c.findStringEntry(label.toUpperCase(), null);
                if (logger.isDebugEnabled() && ret != null) {
                    logger.debug("Found " + ret + " while looking up type for " + label.toUpperCase());
                }
            }
        }

        return ret;
    }

    /**
     * Add an extra mapping into the specified engine
     */
    public void addType(String engine, String label, String value) {
        Map<String, String> extra = extraMapping.get(engine);
        if (extra == null) {
            extra = new HashMap<String, String>();
            extraMapping.put(engine, extra);
        }
        extra.put(label, value);
    }


    /**
     * Chop down to file extension and look it up in specified engine
     */
    public String getFormByExtension(String engine, String fn) {
        if (fn == null) {
            return null;
        }

        int idx = fn.lastIndexOf('.');
        if (idx != -1) {
            return getForm(engine, fn.substring(idx + 1));
        }
        return getForm(engine, fn);
    }
}
