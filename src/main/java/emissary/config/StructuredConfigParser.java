package emissary.config;

import emissary.util.io.ResourceReader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Parses YAML and TOML configs into {@link Configurator} entries (see {@code Sample.yaml} and {@code Sample.toml}).
 * Keys become strings, a blank or comment-only file loads as no entries, and a file may hold only one document. A
 * duplicated mapping key is a startup error in both formats; use a sequence for multi-valued entries. YAML merge keys
 * ({@code <<}) are rejected, so flatten the mapping explicitly instead.
 */
public final class StructuredConfigParser {

    private static final Logger logger = LoggerFactory.getLogger(StructuredConfigParser.class);

    /**
     * A config file holds exactly one document, so a trailing token is an error rather than dropped content. Duplicate
     * mapping keys are likewise an error in both formats, matching the TOML spec.
     */
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectMapper TOML_MAPPER = new ObjectMapper(new TomlFactory())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final ServiceConfigGuide configG;

    /** Leading byte order mark, which is not config content. */
    private static final char BYTE_ORDER_MARK = 0xFEFF;

    /**
     * Create a parser that feeds the given guide.
     *
     * @param configG the service config guide
     */
    public StructuredConfigParser(final ServiceConfigGuide configG) {
        this.configG = configG;
    }

    /**
     * Supported structured config formats.
     */
    enum Format {
        /** YAML files. */
        YAML,
        /** TOML files. */
        TOML;

        /**
         * Collection kind name for error messages: TOML calls them arrays.
         *
         * @return {@code array} for TOML, else {@code sequence}
         */
        String collectionKind() {
            return this == TOML ? "array" : "sequence";
        }
    }

    /**
     * Whether the named config file is YAML.
     *
     * @param filename the config name to check
     * @return true for {@code .yaml} and {@code .yml} names
     */
    public static boolean isYamlFile(final String filename) {
        final String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(ResourceReader.YAML_SUFFIX) || lower.endsWith(ResourceReader.YML_SUFFIX);
    }

    /**
     * Whether the named config file is TOML.
     *
     * @param filename the config name to check
     * @return true for {@code .toml} names
     */
    public static boolean isTomlFile(final String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(ResourceReader.TOML_SUFFIX);
    }

    /**
     * Whether the named config file is YAML or TOML.
     *
     * @param filename the config name to check
     * @return true for structured config names
     */
    public static boolean isStructuredFile(final String filename) {
        return isYamlFile(filename) || isTomlFile(filename);
    }

    /**
     * Parse structured config data into the guide, closing the stream.
     *
     * @param is the stream to read, closed on return
     * @param filename the config name, used for parser dispatch and error messages
     * @throws IOException on syntax errors or unsupported structure
     */
    public void read(final InputStream is, final String filename) throws IOException {
        if (isTomlFile(filename)) {
            readStructured(is, filename, TOML_MAPPER, Format.TOML);
        } else if (isYamlFile(filename)) {
            readStructured(is, filename, YAML_MAPPER, Format.YAML);
        } else {
            is.close();
            throw new IOException("Cannot parse " + filename + ": unknown structured config suffix");
        }
    }

    /**
     * Parse structured config data into entries.
     *
     * @param is the stream to read, closed on return
     * @param filename the config name for error messages
     * @param mapper the format-specific reader
     * @param format the config format
     * @throws IOException on syntax errors or unsupported structure
     */
    private void readStructured(final InputStream is, final String filename, final ObjectMapper mapper, final Format format)
            throws IOException {
        try {
            final byte[] content = is.readAllBytes();
            if (!hasContent(content)) {
                logger.debug("{} config {} has no entries", format, filename);
                return;
            }
            flattenParsed(mapper.readValue(content, Object.class), filename, format);
        } catch (JsonProcessingException e) {
            throw parseFailure(format, filename, e);
        } finally {
            is.close();
        }
    }

    /**
     * Whether a file holds any entry. A blank or comment-only file is an empty config, not a parse error.
     *
     * @param content the raw file bytes
     * @return false when there is nothing to load
     */
    private static boolean hasContent(final byte[] content) {
        for (final String line : new String(content, UTF_8).split("\\R", -1)) {
            final String s = line.trim();
            // Skip an optional byte order mark, then a comment introducer, which is '#' in both formats
            final int start = !s.isEmpty() && s.charAt(0) == BYTE_ORDER_MARK ? 1 : 0;
            if (s.length() > start && s.charAt(start) != '#') {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the failure to throw for a Jackson parse error.
     *
     * @param format the config format
     * @param filename the config name for error messages
     * @param e Jackson parse failure
     * @return the failure to throw
     */
    private static IOException parseFailure(final Format format, final String filename, final JsonProcessingException e) {
        final IOException failure = new IOException("Cannot parse " + format + " config " + parseLocation(filename, e)
                + ": " + e.getOriginalMessage(), e);
        logger.error("{}", failure.getMessage());
        return failure;
    }

    /**
     * Flatten a parsed top-level value into entries.
     *
     * @param parsed the parsed document
     * @param filename the config name for error messages
     * @param format the config format
     * @throws IOException when the top level is not a mapping
     */
    @SuppressWarnings("unchecked")
    private void flattenParsed(final Object parsed, final String filename, final Format format) throws IOException {
        if (parsed instanceof Map) {
            flattenEntries("", "$", (Map<String, Object>) parsed, filename, format, "=");
        } else if (parsed != null) {
            final String kind = parsed instanceof List ? format.collectionKind() : "scalar";
            throw new IOException(format + " config " + filename + " must be a mapping at the top level, found " + kind);
        } else {
            logger.debug("{} config {} is empty, no entries loaded", format, filename);
        }
    }

    /**
     * Jackson failure location as {@code filename:line:column}, or filename when unavailable.
     *
     * @param filename the config name
     * @param e Jackson parse failure
     * @return failure as {@code filename:line:column}, or filename when no location is available.
     */
    private static String parseLocation(final String filename, final JsonProcessingException e) {
        if (e.getLocation() == null || e.getLocation().getLineNr() < 1) {
            return filename;
        }
        return filename + ":" + e.getLocation().getLineNr() + ":" + e.getLocation().getColumnNr();
    }

    /**
     * Whether the key is a top-level operator key
     *
     * @param rawKey the config key
     * @return true when the key is an operator key
     */
    private static boolean isOperatorKey(final String rawKey) {
        return "!remove".equals(rawKey) || "!import".equals(rawKey) || "!opt-import".equals(rawKey)
                || "!=".equals(rawKey);
    }

    /**
     * Flatten one mapping level into config entries.
     *
     * @param prefix flattened key prefix, empty at the top level
     * @param sourcePath dotted source path for error messages
     * @param map the parsed mapping
     * @param filename the config name for error messages
     * @param format the config format
     * @param operatorArg the entry operator ({@code =} or {@code !=})
     * @throws IOException on unsupported structure or entry failures
     */
    @SuppressWarnings("unchecked")
    private void flattenEntries(final String prefix, final String sourcePath, final Map<String, Object> map, final String filename,
            final Format format, final String operatorArg)
            throws IOException {
        for (final Map.Entry<String, Object> e : map.entrySet()) {
            final String rawKey = e.getKey();
            final Object v = e.getValue();
            final String itemPath = sourcePath + "." + rawKey;
            if ("<<".equals(rawKey)) {
                throw new IOException(format + " " + filename + " key \"<<\" at " + itemPath
                        + " is a merge key, which is not supported; flatten the mapping explicitly instead.");
            }
            final boolean insideRemove = prefix.isEmpty() && "!=".equals(operatorArg);
            if ((insideRemove || !prefix.isEmpty()) && isOperatorKey(rawKey)) {
                throw new IOException(format + " " + filename + " key \"" + rawKey + "\" at " + itemPath
                        + (insideRemove ? " is not allowed inside a !remove block" : " is only allowed at the top level"));
            }
            if (prefix.isEmpty() && "!=".equals(rawKey)) {
                throw new IOException(format + " " + filename + " key \"" + rawKey + "\" at " + itemPath
                        + " is an operator, not a key");
            }
            if (prefix.isEmpty() && "!remove".equals(rawKey)) {
                if (!(v instanceof Map)) {
                    throw new IOException(
                            format + " " + filename + " key \"!remove\" at " + itemPath + " must be a mapping, found " + valueKind(v, format));
                }
                flattenEntries("", itemPath, (Map<String, Object>) v, filename, format, "!=");
                continue;
            }
            if (prefix.isEmpty() && ("!import".equals(rawKey) || "!opt-import".equals(rawKey))) {
                final String importKey = "!import".equals(rawKey) ? "IMPORT_FILE" : "OPT_IMPORT_FILE";
                if (v instanceof List) {
                    int i = 0;
                    for (final Object item : (List<Object>) v) {
                        final String elementPath = itemPath + "[" + i++ + "]";
                        if (item instanceof Map || item instanceof List) {
                            throw new IOException(format + " " + filename + " key \"" + rawKey + "\" at " + elementPath
                                    + " must be a scalar or " + format.collectionKind() + " of scalars, found nested " + valueKind(item, format));
                        }
                        addMappedEntry(importKey, item, "=", filename, format, elementPath);
                    }
                } else {
                    if (v instanceof Map) {
                        throw new IOException(format + " " + filename + " key \"" + rawKey + "\" at " + itemPath
                                + " must be a scalar or " + format.collectionKind() + " of scalars, found mapping");
                    }
                    addMappedEntry(importKey, v, "=", filename, format, itemPath);
                }
                continue;
            }
            final String key = prefix.isEmpty() ? rawKey : prefix + "_" + rawKey;
            if (v instanceof Map) {
                flattenEntries(key, itemPath, (Map<String, Object>) v, filename, format, operatorArg);
            } else if (v instanceof List) {
                int i = 0;
                for (final Object item : (List<Object>) v) {
                    final String elementPath = itemPath + "[" + i++ + "]";
                    if (item instanceof List) {
                        throw new IOException(format + " " + filename + " key \"" + key + "\" at " + elementPath
                                + " must be a scalar or " + format.collectionKind() + " of scalars, found nested " + valueKind(item, format));
                    }
                    if (item instanceof Map) {
                        applySequenceOp(key, elementPath, item, filename, format);
                        continue;
                    }
                    addMappedEntry(key, item, operatorArg, filename, format, elementPath);
                }
            } else {
                addMappedEntry(key, v, operatorArg, filename, format, itemPath);
            }
        }
    }

    /**
     * Flatten a sequence entry, which must be a single-entry {@code !remove} mapping.
     *
     * @param key the flattened config key owning the sequence
     * @param elementPath dotted source path of this item
     * @param item the map item
     * @param filename the config name for error messages
     * @param format the config format
     * @throws IOException on unsupported operations
     */
    @SuppressWarnings("unchecked")
    private void applySequenceOp(final String key, final String elementPath, final Object item,
            final String filename, final Format format) throws IOException {
        final Map<String, Object> op = (Map<String, Object>) item;
        if (op.size() == 1 && op.containsKey("!remove")) {
            final Object target = op.get("!remove");
            if (target instanceof List) {
                int i = 0;
                for (final Object sub : (List<Object>) target) {
                    if (sub instanceof Map || sub instanceof List) {
                        throw new IOException(format + " " + filename + " key \"" + key + "\" at " + elementPath + "[" + i + "]"
                                + " must be a scalar or " + format.collectionKind() + " of scalars, found nested " + valueKind(sub, format));
                    }
                    addMappedEntry(key, sub, "!=", filename, format, elementPath + "[" + i++ + "]");
                }
            } else if (!(target instanceof Map)) {
                addMappedEntry(key, target, "!=", filename, format, elementPath);
            } else {
                throw new IOException(format + " " + filename + " key \"" + key + "\" at " + elementPath
                        + " has an unsupported positional operation;"
                        + " sequence maps must be single-entry {\"!remove\": scalar-or-sequence}.");
            }
            return;
        }
        throw new IOException(format + " " + filename + " key \"" + key + "\" at " + elementPath
                + " must be a scalar or " + format.collectionKind() + " of scalars, found nested " + valueKind(item, format)
                + "; sequence maps must be single-entry {\"!remove\": scalar-or-sequence}.");
    }

    /**
     * Single flattened value as a config entry.
     *
     * @param key the flattened config key
     * @param value the raw value
     * @param operatorArg the entry operator ({@code =} or {@code !=})
     * @param filename the config name for error messages
     * @param format the config format
     * @param sourcePath dotted source path for error messages
     * @throws IOException when the value is null, or wrapping the entry failure
     */
    private void addMappedEntry(final String key, final Object value, final String operatorArg, final String filename,
            final Format format, final String sourcePath) throws IOException {
        if (value == null) {
            throw new IOException(format + " " + filename + " key '" + key + "' at " + sourcePath
                    + " has no value; quote an empty string to set a blank value,"
                    + " and use " + ServiceConfigGuide.NULL_VALUE + " to null the entry.");
        }
        if (!isScalar(value)) {
            // A non-scalar would be stored by String.valueOf as a Java object string
            throw new IOException(format + " " + filename + " key '" + key + "' at " + sourcePath
                    + " must be a scalar, found " + valueKind(value, format) + "; write it as text instead.");
        }
        final String sval = String.valueOf(value);
        try {
            configG.handleNewEntry(key, sval, operatorArg, filename, 0, false);
        } catch (IOException e) {
            throw new IOException(
                    format + " " + filename + " entry '" + key + "' at " + sourcePath + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Whether a parsed leaf is a scalar, since config values are text.
     *
     * @param v the parsed value
     * @return true for scalars
     */
    private static boolean isScalar(final Object v) {
        return v instanceof CharSequence || v instanceof Number || v instanceof Boolean || v instanceof Character;
    }

    /** Parsed value kind for error messages. */
    private static String valueKind(final Object v, final Format format) {
        if (v instanceof Map) {
            return "mapping";
        } else if (v instanceof List) {
            return format.collectionKind();
        } else if (v == null) {
            return "null";
        } else if (!isScalar(v)) {
            return v.getClass().getSimpleName();
        }
        return "scalar";
    }
}
