package emissary.config;

import emissary.util.io.ResourceReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * This class assists users and integrators by extracting the named resource and putting it into the config directory
 */
public class ExtractResource {
    /** Our logger */
    protected static final Logger logger = LoggerFactory.getLogger(ExtractResource.class);

    /** The output path for extraction, the first config dir */
    protected String outputDirectory = ConfigUtil.getFirstConfigDir();

    /**
     * Set the output directory for extraction to a new value Default is obtained from ConfigUtil#getConfigDir
     */
    public void setOutputDirectory(final String dir) {
        this.outputDirectory = dir;
    }

    /**
     * Return the extraction output directory
     */
    public String getOutputDirectory() {
        return this.outputDirectory;
    }

    public String getResource(final String theResource) throws IOException {
        String resource = theResource;
        if (!hasFileEnding(resource)) {
            resource += ConfigUtil.CONFIG_FILE_ENDING;
        }
        logger.debug("Reading " + resource);
        final String result;
        try (final InputStream is = ConfigUtil.getConfigStream(resource);
                final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final byte[] buf = new byte[4096];
            int thisReadOp = 0;
            while ((thisReadOp = is.read(buf)) > -1) {
                baos.write(buf, 0, thisReadOp);
            }
            result = baos.toString();
        }
        return result;
    }

    public void writeResource(final String theResource) throws IOException {
        String resource = theResource;
        if (!hasFileEnding(resource)) {
            resource += ConfigUtil.CONFIG_FILE_ENDING;
        }
        final String rezdata = getResource(resource);
        final String outputPath = this.outputDirectory + "/" + resource.replaceAll("/", ".");
        logger.debug("Writing " + outputPath);
        try (final BufferedOutputStream os = new BufferedOutputStream(Files.newOutputStream(Paths.get(outputPath)))) {
            os.write(rezdata.getBytes());
        }
    }

    private static boolean hasFileEnding(final String resource) {
        return resource.endsWith(ResourceReader.CONFIG_SUFFIX) || resource.endsWith(ResourceReader.PROP_SUFFIX)
                || resource.endsWith(ResourceReader.XML_SUFFIX) || resource.endsWith(ResourceReader.JS_SUFFIX);
    }

    /**
     * Run from the command line specifying a set of resources to be extracted
     */
    @SuppressWarnings("SystemOut")
    public static void main(final String[] args) {
        final ExtractResource ex = new ExtractResource();

        if (args.length == 0) {
            printUsage();
            return;
        }

        int i = 0;

        // Process arguments
        while (i < args.length && args[i].startsWith("-")) {
            if ("-o".equals(args[i])) {
                if (args.length >= i + 1) {
                    ex.setOutputDirectory(args[++i]);
                } else {
                    printUsage();
                    return;
                }
            } else if ("--".equals(args[i])) {
                i++;
                break;
            } else {
                printUsage();
                return;
            }
            i++;
        }

        // Process remaining args as resource names
        for (; i < args.length; i++) {
            try {
                ex.writeResource(args[i]);
            } catch (IOException iox) {
                logger.error("{}: {}", args[i], iox.toString());
            }
        }
    }

    @SuppressWarnings("SystemOut")
    private static void printUsage() {
        System.out.println("Usage: scripts/run.sh " + ExtractResource.class.getName() + " [ -o output_directory ] package/to/Resource[.cfg] ...");
    }
}
