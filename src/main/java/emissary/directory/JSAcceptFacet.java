package emissary.directory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Facet;
import emissary.core.IAggregator;
import emissary.core.IBaseDataObject;
import emissary.log.MDCConstants;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Controls lookup and caching of pertinent javascript accept functions for the relevant itinerary steps.
 *
 * Javascript accept functions must be of the signature boolean accept(IBaseDataObject d) and return true if the service
 * accepts the payload, false otherwise.
 *
 * Javascript functions can come from:
 * <ol>
 * <li>The jar files on the classpath as resources</li>
 * <li>.js files in the config directory</li>
 * <li>Supply by external sources, possibly remote</li>
 * </ol>
 * Only scripts read in from the config directory can be reread and updated if the lastModified time on the file
 * changes. The lastModified time will be checked every CACHE_CHECKPOINT_MILLIS. Scripts read from resources will still
 * be replaced by a copy put into the config directory and updated, but scripts provided externally cannot be
 * overridden. If you provide a script externally with {@link #add(emissary.directory.DirectoryEntry,String,boolean)
 * add} and want to override, it must be removed first using {@link #remove(emissary.directory.DirectoryEntry) remove}
 * or {@link #removeDefault(emissary.directory.DirectoryEntry) removeDefault}
 *
 * The category for the script logger is BASE_PACKAGE.Script which is different from this class's logger, but all
 * scripts run in the same logger category. The default BASE_PACKAGE value is {@value #DEFAULT_BASE_PACKAGE}
 */
public class JSAcceptFacet extends Facet {
    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(JSAcceptFacet.class);

    /** The facet name value is {@value} */
    public static final String JSACCEPT_FACET_NAME = "jsaccept";

    /** Default min time to recheck a disk file for updates value {@value} */
    public static final long DEFAULT_CACHE_CHECKPOINT_MILLIS = 60000;

    /** Actual min time to recheck a disk for for updates */
    protected long CACHE_CHECKPOINT_MILLIS = DEFAULT_CACHE_CHECKPOINT_MILLIS;

    /** Default base package value is {@value} */
    public static final String DEFAULT_BASE_PACKAGE = "emissary.directory.js.";

    /** Base package of JSAccept Resources, should end with a dot */
    protected String BASE_PACKAGE = DEFAULT_BASE_PACKAGE;

    /** The Logger to provide to Rhino for scripts */
    private Logger scriptLogger = LoggerFactory.getLogger(this.BASE_PACKAGE + "Script");

    /** Cache of javascript functions for specific DATA_ID like keys */
    Map<String, Function> funcCache = new ConcurrentHashMap<String, Function>();

    /** Cache of function timestamps for relaoading DATA_ID like keys */
    Map<String, Long> funcLoadMap = new ConcurrentHashMap<String, Long>();

    /** The name of the Javascript function to use for routing is {@value} */
    public static final String ACCEPT_FUNCTION_NAME = "accept";

    /** The global context factory */
    protected ContextFactory contextFactory = ContextFactory.getGlobal();

    /**
     * Create and configure the JSAccept facet
     */
    public JSAcceptFacet() {
        setName(JSACCEPT_FACET_NAME);
        configureFacet();
    }

    /**
     * Create and configure the JSAccept facet
     *
     * @param config the configurator to use
     */
    public JSAcceptFacet(final Configurator config) {
        setName(JSACCEPT_FACET_NAME);
        configureFacet(config);
    }

    /**
     * Configure the facet using the default config locations
     *
     * @see #configureFacet(emissary.config.Configurator config)
     */
    protected void configureFacet() {
        try {
            final Configurator config = ConfigUtil.getConfigInfo(this.getClass());
            configureFacet(config);
        } catch (IOException ignore) {
            // empty catch block
        }
    }

    /**
     * Configure the facet using the specified config info. Items for configuration are
     * <ul>
     * <li>CACHE_CHECKPOINT_MILLIS: number of millis for disk check to find updated JS files</li>
     * <li>BASE_PACKAGE: base package for JSAccept resources</li>
     * </ul>
     *
     * @param config the configurator to use
     */
    protected void configureFacet(final Configurator config) {
        this.CACHE_CHECKPOINT_MILLIS = config.findLongEntry("CACHE_CHECKPOINT_MILLIS", DEFAULT_CACHE_CHECKPOINT_MILLIS);
        setBasePackage(config.findStringEntry("BASE_PACKAGE", DEFAULT_BASE_PACKAGE));
    }

    /**
     * Reset the base package for resource location.
     *
     * @param packageName the new base package, value should end with a dot
     */
    public void setBasePackage(final String packageName) {
        // Save value and reset the scriptLogger if a change
        if (!this.BASE_PACKAGE.equals(packageName)) {
            this.BASE_PACKAGE = packageName;
            this.scriptLogger = LoggerFactory.getLogger(this.BASE_PACKAGE + "Script");
        }
    }

    /**
     * Lookup method to retrieve the JSAccept Facet
     *
     * @param obj the aggregate object to inspect
     * @return the JSAccept facet or null if none found
     * @throws Exception if the facet is the wrong type
     */
    public static JSAcceptFacet of(final IAggregator obj) throws Exception {
        if (obj == null) {
            return null;
        }

        final Facet f = Facet.of(obj, JSACCEPT_FACET_NAME);

        if (f != null && !(f instanceof JSAcceptFacet)) {
            throw new Exception("Wrong facet type: " + f.getClass().getName());
        }
        return (JSAcceptFacet) f;
    }

    /**
     * Test the specified payload and itinerary entries for acceptance. Itinerary steps will be removed from the list if
     * they are not accepted.
     *
     * @param payload the data object being routed
     * @param itinerary list of keys selected so far, can be modified
     */
    public void accept(final IBaseDataObject payload, final List<DirectoryEntry> itinerary) {
        // Check conditions
        if (payload == null || itinerary == null) {
            logger.debug("Cannot operate on null payload or null itinerary");
            return;
        }

        if (itinerary.size() == 0) {
            return;
        }

        final Context ctx = this.contextFactory.enterContext();
        try {
            // One standard scope will do
            final Scriptable scope = makeAcceptFacetScope(ctx);

            // Get a function for each itinerary key present
            // and call the accept method on it
            // nb: no enhanced for loop due to i.remove() below
            for (Iterator<DirectoryEntry> i = itinerary.iterator(); i.hasNext();) {
                final DirectoryEntry de = i.next();
                logger.debug("Looking at itinerary item " + de.getKey());
                final Function func = getFunction(de);
                if (func == null) {
                    continue;
                }

                MDC.put(MDCConstants.SERVICE_LOCATION, getResourceName(de));
                try {
                    final Object accepted = func.call(ctx, scope, scope, new Object[] {payload});

                    // If the javascript function says not to accept
                    // we remote the key from the itinerary here
                    if (accepted != Scriptable.NOT_FOUND && Boolean.FALSE.equals(accepted)) {
                        logger.debug("Removing directory entry " + getFunctionKey(de) + " due to js function false");
                        i.remove();
                    }
                } catch (Exception ex) {
                    logger.warn("Unable to run function for " + getFunctionKey(de), ex);
                } finally {
                    MDC.remove(MDCConstants.SERVICE_LOCATION);
                }
            }
        } finally {
            Context.exit();
        }
    }

    /**
     * Get the js key that would match a directory entry and will allow mapping the resource
     *
     * @param de the controlling directory entry
     * @return the string key
     */
    public String getFunctionKey(final DirectoryEntry de) {
        /*
         * f(UNKONWN.UnixFile.ID.http://example.com:8001/UnixFilePlace) => UnixFile.UNKOWN
         */
        return de.getServiceName() + "." + de.getDataType();
    }

    /**
     * Get the js key that would match a defaulted directory entry to allow mapping the resource. It is as if the directory
     * entry key were wildcarded on the SERVICE_PROXY element and we just omit it from the key.
     *
     * @param de the controlling directory entry
     * @return the string key
     */
    public String getDefaultFunctionKey(final DirectoryEntry de) {
        /*
         * f(UNKONWN.UnixFile.ID.http://example.com:8001/UnixFilePlace) => UnixFile
         */
        return de.getServiceName();
    }

    /**
     * Get the js resource name that would match a directory entry and will allow finding the resource
     *
     * @param de the controlling directory entry
     * @return the resource name
     */
    public String getResourceName(final DirectoryEntry de) {
        /*
         * f(UNKONWN.UnixFile.ID.http://example.com:8001/UnixFilePlace) => #{BASE_PACKAGE}.UnixFile.UNKOWN.js
         */
        return this.BASE_PACKAGE + getFunctionKey(de) + ConfigUtil.JS_FILE_ENDING;
    }

    /**
     * Get the default js resource name that would match a directory entry wildcarded on the SERVICE_PROXY element and will
     * allow finding the resource
     *
     * @param de the controlling directory entry
     * @return the resource name
     */
    public String getDefaultResourceName(final DirectoryEntry de) {
        /*
         * f(UNKONWN.UnixFile.ID.http://example.com:8001/UnixFilePlace) => #{BASE_PACKAGE}.UnixFile.js
         */
        return this.BASE_PACKAGE + getDefaultFunctionKey(de) + ConfigUtil.JS_FILE_ENDING;
    }

    /**
     * Get a script matching the directory entry from the local script cache or from a provided resource. If we retrieve
     * from a resource the result will be cached even if null to prevent future lookups and compilations. This is a
     * timestamp enabled cache, such that editing the JS resource on disk will cause it to be reloaded and recompiled. JS
     * resources provided in jar files are never reloaded, but if initially loaded from a jar file, placing on in the proper
     * disk location will cause the disk file resource to override at the next checkpoint.
     *
     * @param de the directory entry to match
     * @return the script
     */
    protected Function getFunction(final DirectoryEntry de) {
        final String key = getFunctionKey(de);
        final String defKey = getDefaultFunctionKey(de);
        boolean usingDefault = false;

        long ts = 0L; // new read default
        final long now = System.currentTimeMillis();

        // Check saved timestamp
        if (this.funcLoadMap.containsKey(key)) {
            ts = this.funcLoadMap.get(key).longValue();
        } else if (this.funcLoadMap.containsKey(defKey)) {
            ts = this.funcLoadMap.get(defKey).longValue();
            usingDefault = true;
        }

        if (ts == -1) {
            ts = now; // a non-upating function
        }

        logger.debug("getFunction called for " + de.getKey() + " usingDefault? " + usingDefault + " ts=" + ts);

        if (!usingDefault) {
            // Has been read in last x millis, use it
            if (ts > now - this.CACHE_CHECKPOINT_MILLIS && this.funcCache.containsKey(key)) {
                logger.debug("Function " + key + " is not out of date ts=" + ts + ", now=" + now + ", now-ts=" + (now - ts));
                return this.funcCache.get(key);
            }
        } else {
            // Has default been read in last x millis, use it
            if (ts > now - this.CACHE_CHECKPOINT_MILLIS && this.funcCache.containsKey(defKey)) {
                logger.debug("Function " + defKey + " is not out of date ts=" + ts + ", now=" + now + ", now-ts=" + (now - ts));
                return this.funcCache.get(defKey);
            }
        }

        // Read the non-default function
        Function func = readFunction(de, ts, false);

        // Check for default if none found
        if (func == null) {
            func = readFunction(de, ts, true);
            if (func != null) {
                usingDefault = true;
            }
        }

        final String mapKey = usingDefault ? defKey : key;

        // Cache the ts for a null read on a new item
        if (func == null && ts == 0L) {
            logger.debug("Got a null func for " + key + " on a ts=0L read");
            this.funcCache.remove(mapKey);
            this.funcLoadMap.put(mapKey, Long.valueOf(now));
            return null;
        }

        // If a null read for a not-new item, return previous
        // as it is not out of date, update timestamp of read check
        if (func == null && ts >= 0L) {
            logger.debug("No new update on func " + mapKey + " ts=" + ts + ", now=" + now);
            this.funcLoadMap.put(mapKey, Long.valueOf(now));
            return this.funcCache.get(mapKey);
        }

        // If we read a function, cache it
        if (func != null && ts != -1L) {
            this.funcLoadMap.put(mapKey, Long.valueOf(now));
            this.funcCache.put(mapKey, func);
        }

        logger.debug("Read and cached a script func for " + mapKey + " => " + func);

        return func;
    }

    /**
     * Read a func file and compile the func
     *
     * @param de the controlling directory entry
     * @param ts last time we read it, or 0L for new read
     * @param useDefault check for default version
     * @return the compiled func or null
     */
    protected Function readFunction(final DirectoryEntry de, final long ts, final boolean useDefault) {
        final String rezName = useDefault ? getDefaultResourceName(de) : getResourceName(de);
        final long lastModified = ConfigUtil.getConfigFileLastModified(rezName);
        if (ts > 0L && lastModified < ts) {
            // It's not newer than the specified timestamp
            logger.debug("Func is not newer, ts=" + ts + " lastMod=" + lastModified + " returning null from readFunction");
            return null;
        }

        logger.debug("Looking for function to read as resource " + rezName + " ts=" + ts + " lastModified=" + lastModified + ", default? "
                + useDefault);
        String source = null;
        Function func = null;
        InputStream is = null;
        final Context ctx = this.contextFactory.enterContext();
        try {
            final Scriptable scope = makeAcceptFacetScope(ctx);
            is = ConfigUtil.getConfigStream(rezName);
            final byte[] data = new byte[is.available()];
            is.read(data);
            source = new String(data);
            // args: (scope, source, sourceName, startingLineNum, securityDomain)
            ctx.evaluateString(scope, source, rezName, 1, null);
            final Object afunc = scope.get(ACCEPT_FUNCTION_NAME, scope);
            if (afunc instanceof Function) {
                func = (Function) afunc;
                if (logger.isDebugEnabled()) {
                    logger.debug("Got the accept function from " + rezName + ctx.decompileFunction(func, 3));
                }
            } else {
                logger.warn("No accept function found in " + rezName + " but isa " + (afunc == null ? "<null>" : afunc.getClass().getName())
                        + " from " + source);
            }

        } catch (IOException iox) {
            // Expected when not using js facet acceptor for this resource
            // logger.debug("No such config stream: " + rezName);
        } catch (Exception ex) {
            logger.error("Cannot compile function for " + rezName, ex);
            logger.debug("Function source was " + source);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignore) {
                    // empty catch block
                }
            }
            Context.exit();
        }

        return func;
    }

    /**
     * Make a scope in the current context with our required global variables in it
     *
     * @param ctx the current Rhino context
     * @return the new scope
     */
    protected Scriptable makeAcceptFacetScope(final Context ctx) {
        final Scriptable scope = ctx.initStandardObjects();
        final Object jsLogger = Context.javaToJS(this.scriptLogger, scope);
        ScriptableObject.putProperty(scope, "logger", jsLogger);
        final Object jsOut = Context.javaToJS(System.out, scope);
        ScriptableObject.putProperty(scope, "out", jsOut);
        return scope;
    }

    /**
     * Allow a function to be loaded into the facet after being pushed here by a possibly remote source. These are stored
     * with -1L timestamp so that the local disk and resources are never checked for an update. If you want to have the
     * local config streamuration take over for this directory entry, this script must first be removed
     *
     * @see #remove(emissary.directory.DirectoryEntry)
     * @param de the DirectoryEntry the function matches
     * @param script the javascript text of the function
     * @param isDefault true if function is added as default entry for the key as if SERVICE_PROXY were wildcarded
     * @return true if the function compiles and is stored
     * @throws Exception if function provided does not compile
     */
    public boolean add(final DirectoryEntry de, final String script, final boolean isDefault) throws Exception {
        final String key = isDefault ? getDefaultFunctionKey(de) : getFunctionKey(de);
        Function func = null;
        final Context ctx = this.contextFactory.enterContext();
        try {
            final Scriptable scope = makeAcceptFacetScope(ctx);
            ctx.evaluateString(scope, script, key, 1, null);
            final Object afunc = scope.get(ACCEPT_FUNCTION_NAME, scope);
            if (afunc instanceof Function) {
                func = (Function) afunc;
                this.funcCache.put(key, func);
                this.funcLoadMap.put(key, Long.valueOf(-1L));
                logger.debug("Added function on key " + key + " isDefault? " + isDefault);
            } else {
                logger.warn("Javascript did not have the required function: " + script + " on key " + key);
            }
        } catch (Exception ex) {
            logger.error("Cannot compile function for " + key, ex);
            logger.debug("Function source was " + script);
            throw ex;
        } finally {
            Context.exit();
        }
        return func != null;
    }

    /**
     * Let the caller know if there is a routing function supplied for the specified directory entry
     *
     * @param de the directory entry
     * @return true if there is non-null Function in the cache
     */
    public boolean containsRoutingFor(final DirectoryEntry de) {
        String key = getFunctionKey(de);
        Function function = this.funcCache.get(key);
        if (function == null) {
            key = getDefaultFunctionKey(de);
            function = this.funcCache.get(key);
        }
        return (function != null);
    }

    /**
     * Let the caller remove a routing function
     *
     * @param de the directory entry to use as a key
     * @return true if a function was removed
     */
    public boolean remove(final DirectoryEntry de) {
        final String key = getFunctionKey(de);
        this.funcLoadMap.remove(key);
        final Function f = this.funcCache.remove(key);
        return f != null;
    }

    /**
     * Let the caller remove a routing function
     *
     * @param de the directory entry to use as a key
     * @return true if a function was removed
     */
    public boolean removeDefault(final DirectoryEntry de) {
        final String key = getDefaultFunctionKey(de);
        this.funcLoadMap.remove(key);
        final Function f = this.funcCache.remove(key);
        return f != null;
    }
}
