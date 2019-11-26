package emissary.util.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class reads a resource with utilities to read those with common names
 */
public class ResourceReader {

    private static final Logger logger = LoggerFactory.getLogger(ResourceReader.class);

    public static final String CONFIG_SUFFIX = ".cfg";
    public static final String XML_SUFFIX = ".xml";
    public static final String DATA_SUFFIX = ".dat";
    public static final String JS_SUFFIX = ".js";
    public static final String PROP_SUFFIX = ".properties";
    public static final String CLASS_SUFFIX = ".class";

    /**
     * Create a resource reader for use
     */
    public ResourceReader() {}

    /**
     * Return the config stream for the class config file Caller must close the stream
     * 
     * @param o the object whose class name matches the resource
     */
    public InputStream getConfigDataAsStream(Object o) {
        return getConfigDataAsStream(o.getClass());
    }

    /**
     * Return the config stream for the class config file Caller must close the stream
     * 
     * @param c the class name matching the desired resource
     */
    public InputStream getConfigDataAsStream(Class<?> c) {
        String name = getConfigDataName(c);
        return getResourceAsStream(name);
    }

    /**
     * Get the config name
     */
    public String getConfigDataName(Class<?> c) {
        return getResourceName(c) + CONFIG_SUFFIX;
    }

    /**
     * Return the stream for the class xml resource file Caller must close the stream
     * 
     * @param o the object whose class name matches the resource
     */
    public InputStream getXmlStream(Object o) {
        return getXmlStream(o.getClass());
    }

    /**
     * Return the xml stream for the class resource Caller must close the stream
     * 
     * @param c the class name matching the desired resource
     */
    public InputStream getXmlStream(Class<?> c) {
        String name = getXmlName(c);
        return getResourceAsStream(name);
    }

    /**
     * Get the xml name
     */
    public String getXmlName(Class<?> c) {
        return getResourceName(c) + XML_SUFFIX;
    }

    /**
     * Get the xml name
     */
    public String getXmlName(Package pkg, String name) {
        return getResourceName(pkg, name) + XML_SUFFIX;
    }

    /**
     * Get the resource name
     */
    public String getResourceName(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    /**
     * Get the resource name
     */
    public String getResourceName(Package pkg, String name) {
        return (pkg.getName() + "/" + name).replace('.', '/');
    }

    /**
     * Get the url of the specified resource
     * 
     * @param name name of the resource
     * @return a url to the resource
     */
    public URL getResource(String name) {
        if (name != null && name.length() > 1 && name.charAt(1) == ':') {
            name = name.substring(2);
        }
        URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource(name);
        return resourceUrl;
    }

    /**
     * Get the specified resource
     * 
     * @param name name of resource
     * @return stream, caller must close
     */
    public InputStream getResourceAsStream(String name) {
        if (name != null && name.length() > 1 && name.charAt(1) == ':') {
            name = name.substring(2);
        }
        InputStream configStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        return configStream;
    }

    /**
     * Indicate the URL that the specified class was loaded from
     * 
     * @param c the class
     * @return a URL indicating the jar file or file location or null if none
     */
    public URL which(Class<?> c) {
        if (c == null) {
            return null;
        }
        String cx = getResourceName(c) + CLASS_SUFFIX;
        ClassLoader loader = c.getClassLoader();
        if (loader != null) {
            return loader.getResource(cx);
        }
        return ClassLoader.getSystemResource(cx);
    }

    /**
     * Find all the config resources present for the specified class
     * 
     * @param c the class
     * @return sorted list of resources found or an empty list if none
     */
    public List<String> findConfigResourcesFor(Class<?> c) {
        return findResourcesFor(c, CONFIG_SUFFIX);
    }

    /**
     * Find all the data resources present for the specified class
     * 
     * @param c the class
     * @return sorted list of resources found or an empty list if none
     */
    public List<String> findDataResourcesFor(Class<?> c) {
        return findResourcesFor(c, DATA_SUFFIX);
    }

    /**
     * Find all the xml resources present for the specified class
     * 
     * @param c the class
     * @return sorted list of resources found or an empty list if none
     */
    public List<String> findXmlResourcesFor(Class<?> c) {
        return findResourcesFor(c, XML_SUFFIX);
    }

    /**
     * Find all the properties resources present for the specified class
     * 
     * @param c the class
     * @return sorted list of resources found or an empty list if none
     */
    public List<String> findPropertyResourcesFor(Class<?> c) {
        return findResourcesFor(c, PROP_SUFFIX);
    }

    /**
     * Find all the resources present for the specified class that use the indicated suffix.
     * 
     * @param c the class
     * @param suffix the resource suffix to hunt for, use "" for all
     * @return sorted list of resources found or an empty list if none
     */
    public List<String> findResourcesFor(Class<?> c, String suffix) {
        List<String> results = new ArrayList<String>();
        URL url = which(c);
        if (url == null) {
            return results;
        }

        if (url.getProtocol().equals("jar")) {
            results.addAll(getJarResourcesFor(c, url, suffix));
        } else if (url.getProtocol().equals("file")) {
            results.addAll(getFileResourcesFor(c, url, suffix));
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Find resources for the specified class from the Jar URL This finds resources at multiple levels at ones. For example
     * if you pass in emissary.util.Version.class with the ".cfg" suffix, you could get back resources that are located at
     * emissary/util/Version.cfg and emissary/util/Version/foo.cfg in the list.
     * 
     * @param c the class
     * @param url the jar url
     * @param suffix the ending suffix of desired resources
     * @return list of resources found
     */
    public List<String> getJarResourcesFor(Class<?> c, URL url, String suffix) {
        List<String> results = new ArrayList<String>();
        try {
            JarURLConnection jc = (JarURLConnection) url.openConnection();
            JarFile jf = jc.getJarFile();
            String cmatch = getResourceName(c);
            for (Enumeration<JarEntry> entries = jf.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(cmatch) && name.endsWith(suffix)) {
                    results.add(name);
                }
            }
            logger.debug("Found {} jar resources for {}", results.size(), cmatch);
        } catch (IOException ex) {
            logger.warn("Cannot get jar url connection to {}", url, ex);
        }
        return results;
    }


    /**
     * Find resources for the specified class from the file URL This finds resources at multiple levels at ones. For example
     * if you pass in emissary.util.Version.class with the ".cfg" suffix, you could get back resources that are located at
     * emissary/util/Version.cfg and emissary/util/Version/foo.cfg in the list.
     * 
     * @param c the class
     * @param url the file url
     * @param suffix the ending suffix of desired resources
     * @return list of resources found
     */
    public List<String> getFileResourcesFor(Class<?> c, URL url, String suffix) {
        List<String> results = new ArrayList<String>();
        String cmatch = getResourceName(c);

        // The url may or may not have the class portion on it
        String path = url.getPath();
        if (path.indexOf(CLASS_SUFFIX) > -1) {
            // Take off the ".class"
            path = path.substring(0, path.length() - CLASS_SUFFIX.length());
        } else {
            // Add on the package and class names
            path += "/" + cmatch;
        }

        // Look for a base resource at same level as class (in package dir)
        File base = new File(path.substring(0, path.lastIndexOf('/')));
        String pkgNamePart = cmatch.substring(0, cmatch.lastIndexOf('/'));
        String classNamePart = cmatch.substring(cmatch.lastIndexOf('/') + 1);
        if (base.exists() && base.isDirectory()) {
            String[] list = base.list();
            for (int i = 0; i < list.length; i++) {
                if (list[i].startsWith(classNamePart) && list[i].endsWith(suffix)) {
                    results.add(pkgNamePart + "/" + list[i]);
                }
            }
        }

        // Look for more resources in a directory with the class name
        File dir = new File(path);
        if (dir.isDirectory()) {
            String[] list = dir.list();
            for (int i = 0; i < list.length; i++) {
                if (list[i].endsWith(suffix)) {
                    results.add(cmatch + '/' + list[i]);
                }
            }
        }

        logger.debug("Found {} file resources for {}", results.size(), cmatch);
        return results;
    }

}
