package emissary.parser;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.util.shell.Executrix;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Provide a factory for getting the proper type of input parser Provide the implementing classes for that match the
 * configured Data Identifier Engine in PARSER_NIO_IMPL_[type] variants if available. All configured parsers must
 * implement emissary.parser.SessionParser.
 *
 * When no proper mappings are found or the specified parser cannot be instantiated, the SimpleNioParser is used
 * instead. If these cannot be instantiated, then something is likely seriously wrong.
 *
 * If an NIO parser is requested, see makeSessionParser(FileChannel), but cannot be found for the data type, the Channel
 * is evaluated and if under MAX_NIO_FALLBACK_SIZE, then the bytes are consumed and a standard parser is produced if one
 * is available.
 *
 * The ID engine is configured with the ID_ENGINE_CLASS in the configuration file and must be an instance of
 * emissary.parser.DataIdentifier.
 */
public class ParserFactory {
    // Logger
    protected static final Logger logger = LoggerFactory.getLogger(ParserFactory.class.getName());

    // Map of dataType to FileChannel parser implementation class name
    // Read from config file
    protected Map<String, String> nioTypeMap = new HashMap<>();

    // For channel sizes larger than this no fallback to a byte[]
    // parser is attempted.
    protected long nioFallbackMax = 1024L * 1024L * 100L; // 100 Mb

    protected static final String DEFAULT_NIO_PARSER = "emissary.parser.SimpleNioParser";
    protected String nioParser = DEFAULT_NIO_PARSER;


    // Data type identification engine
    @Nullable
    DataIdentifier idEngine = null;

    /**
     * Public constructor causes default configuration to be read
     */
    public ParserFactory() {
        reconfigure();
    }

    /**
     * Construct factory with specified configuration
     *
     * @param config the configuration to use for this instance
     * @since 3.7.1
     */
    public ParserFactory(Configurator config) {
        reconfigure(config);
    }

    /**
     * Configure this factory with default config (for keeping the API backward compatible)
     */
    public void reconfigure() {
        reconfigure(null);
    }

    /**
     * Reconfigure the factory causes the configuration to be reloaded It is not threadsafe to call this while data is being
     * identified or parsers are being instantiated.
     *
     * @param config the configuration to use or null for the default
     * @since 3.7.1
     */
    public void reconfigure(@Nullable Configurator config) {
        try {
            if (config == null) {
                config = ConfigUtil.getConfigInfo(ParserFactory.class);
            }

            Map<String, String> m = config.findStringMatchMap("PARSER_NIO_IMPL_", Configurator.PRESERVE_CASE);

            nioFallbackMax = config.findSizeEntry("MAX_NIO_FALLBACK_SIZE", nioFallbackMax);

            nioTypeMap.clear();
            nioTypeMap.putAll(m);

            logger.debug("Loaded {} nio parsers with fallback size {}", nioTypeMap.size(), nioFallbackMax);

            // change this to "DEFAULT_PARSER"
            nioParser = config.findStringEntry("DEFAULT_NIO_PARSER", DEFAULT_NIO_PARSER);

            String idEngineClass = config.findStringEntry("ID_ENGINE_CLASS", null);

            if (idEngineClass != null) {
                makeIdEngine(idEngineClass);
            }
        } catch (IOException ex) {
            logger.error("Unable to read configuration", ex);
        }
    }

    /**
     * Make a session parser with the data in channel. If no NIO parser is configured for the type of this data, a standard
     * byte[] parser will be produced if there is one available and the size of the data in the channel is less than the
     * configured MAX_NIO_FALLBACK_SIZE. Otherwise the default NIO parser will be used.
     *
     * @param channel the data to be parsed
     * @return SessionParser implementation
     */
    public SessionParser makeSessionParser(SeekableByteChannel channel) {
        String id = identify(channel);
        return makeSessionParser(id, channel);
    }

    /**
     * Make a session parser with the data in channel. If no NIO parser is configured for the type of this data, a standard
     * byte[] parser will be produced if there is one available and the size of the data in the channel is less than the
     * configured MAX_NIO_FALLBACK_SIZE. Otherwise the default NIO parser will be used.
     *
     * @param type the type of data
     * @param channel the data to be parsed
     * @return SessionParser implementation
     * @deprecated use {@link #makeSessionParser(SeekableByteChannel, String)}
     */
    @Deprecated
    @SuppressWarnings("InconsistentOverloads")
    public SessionParser makeSessionParser(String type, SeekableByteChannel channel) {
        return makeSessionParser(channel, type);
    }

    /**
     * Make a session parser with the data in channel. If no NIO parser is configured for the type of this data, a standard
     * byte[] parser will be produced if there is one available and the size of the data in the channel is less than the
     * configured MAX_NIO_FALLBACK_SIZE. Otherwise the default NIO parser will be used.
     *
     * @param channel the data to be parsed
     * @param type the type of data
     * @return SessionParser implementation
     */
    public SessionParser makeSessionParser(SeekableByteChannel channel, String type) {
        SessionParser sp;

        if (nioTypeMap.containsKey(type)) {
            sp = makeSessionParserClass(nioTypeMap.get(type), channel);
        } else {
            sp = makeSessionParserClass(nioParser, channel);
        }
        return sp;
    }

    /**
     * Make a session parser for the specified data type with the args
     *
     * @param clazz the class name of the parser to create
     * @param args arguments to the parser constructor
     * @return SessionParser implementation
     */
    @Nullable
    protected SessionParser makeSessionParserClass(@Nullable String clazz, Object... args) {
        // Choose implementation class based on data type
        if (clazz == null) {
            logger.warn("Cannot make a session parser for a null class");
            return null;
        }

        SessionParser sp = null;

        try {
            sp = (SessionParser) Factory.create(clazz, args);
        } catch (RuntimeException e) {
            logger.error("Unable to instantiate {}", clazz, e);
        }

        return sp;
    }

    /**
     * Instantiate the specified DataIdentifier class for typing the data
     */
    protected void makeIdEngine(String clazz) {
        try {
            DataIdentifier d = (DataIdentifier) Factory.create(clazz);
            idEngine = d;
        } catch (RuntimeException ex) {
            logger.warn("Cannot make data identifier from " + clazz, ex);
        }
    }


    /**
     * Return the key identification type fo the data in the channel
     *
     * @param channel the channel containing bytes to identify
     * @return string matching the keys in ParserFactory.cfg
     */
    public String identify(SeekableByteChannel channel) {
        if (idEngine != null) {
            try {
                long pos = channel.position();
                byte[] buf = Executrix.readDataFromChannel(channel, pos, idEngine.DATA_ID_STR_SZ);
                channel.position(pos);
                return idEngine.identify(buf);
            } catch (IOException e) {
                logger.warn("Unable to reposition file channel", e);
            }
        }
        return DataIdentifier.UNKNOWN_TYPE;
    }

}
