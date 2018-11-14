package emissary.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.common.collect.TreeMultimap;
import emissary.core.MetadataDictionary;
import emissary.core.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class takes an alternate view byte stream and changes the metadata labels to be consistent with the
 * MetadataDictionary
 */
public class MetadataDictionaryUtil {
    // The metadata dictionary to use
    MetadataDictionary dict = null;

    // Service prefix to apply to all keys
    String servicePrefix = null;

    // Our friendly logger
    private Logger logger = LoggerFactory.getLogger(MetadataDictionaryUtil.class);

    // The charset of the data
    private String charset = "UTF-8";

    // separator for key and value
    private static final char SEP = ' ';

    public MetadataDictionaryUtil(final String servicePrefix) throws NamespaceException {
        this.dict = MetadataDictionary.lookup();
        this.servicePrefix = servicePrefix;
    }

    public MetadataDictionaryUtil(final MetadataDictionary dict, final String servicePrefix) {
        this.dict = dict;
        this.servicePrefix = servicePrefix;
    }

    public void setCharset(final String cs) {
        this.charset = cs;
    }

    /**
     * Get the view, map the first token on each line. The view should be structured as "key value" where the first space on
     * the line separates the key from the rest.
     * 
     * @param input the bytes of the view to be mapped
     * @return a byte[] containing transformed/alphabetized keys with their value pairs
     */
    public byte[] map(final byte[] input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final Map<String, Collection<String>> kv = convertLinesToMap(input, output);
            return convertMapToByteArray(kv, output);
        }
    }

    /**
     * Converts a map of key/values into a byte array. Each key/value will be output on a separate line in alphabetical
     * order by key in map.
     * 
     * @param metadata the Map of metadata to convert
     * @return a byte[] of key/values on individual lines in alphabetical order
     * @throws IOException If there is some I/O problem.
     */
    public static byte[] convertMapToByteArray(final Map<String, Collection<String>> metadata) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            return convertMapToByteArray(metadata, output);
        }
    }

    /**
     * Converts a map of key/values into a byte array. Each key/value will be output on a separate line in alphabetical
     * order by key in map.
     * 
     * @param kv the Map of metadata to convert
     * @param output a ByteArrayOutputStream to write the key/value pairs to
     * @return a byte[] of key/values on individual lines in alphabetical order
     * @throws IOException If there is some I/O problem.
     */
    public static byte[] convertMapToByteArray(final Map<String, Collection<String>> kv, final ByteArrayOutputStream output) throws IOException {
        // Output the mapped tokens in the revised alphabetical order
        for (final Entry<String, Collection<String>> entry : kv.entrySet()) {
            final String key = entry.getKey();

            for (final String v : entry.getValue()) {
                output.write(key.getBytes());
                output.write(SEP);
                output.write(v.getBytes());
                output.write('\n');
            }
        }

        return output.toByteArray();
    }

    /**
     * Read each line of input, tokenize them into key/value pairs, perform a lookup/transformation of the keys via the
     * MetadataDictionary where applicable, and return the results as a map.
     * 
     * @param input the bytes to convert
     * @param output a ByteArrayOutputStream to write failed parse attempts to
     * @return a Map containing each line converted to a key/value pair and sorted alphabetically by key
     * @throws IOException If there is some I/O problem.
     */
    public Map<String, Collection<String>> convertLinesToMap(final byte[] input, final ByteArrayOutputStream output) throws IOException {
        final TreeMultimap<String, String> kv = TreeMultimap.create();
        final LineTokenizer ltok = new LineTokenizer(input, this.charset);

        // Look at each line for a key value and run it through the dictionary
        while (ltok.hasMoreTokens()) {
            final String line = ltok.nextToken();
            final int pos = line.indexOf(SEP);
            if (pos == -1) {
                output.write(line.getBytes());
                output.write('\n');
                this.logger.debug("Found no key/value pair on line " + line);
            } else {
                final String key = line.substring(0, pos);
                final String value = line.substring(pos + 1);
                final String nkey = this.dict.map(this.servicePrefix + key);
                kv.put(nkey, value.trim());
                this.logger.debug("Mapped key " + key + " to " + nkey + ": " + value);
            }
        }
        return new TreeMap<>(kv.asMap());
    }
}
