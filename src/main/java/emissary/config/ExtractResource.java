package emissary.config;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import emissary.util.io.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        final InputStream is = ConfigUtil.getConfigStream(resource);
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int thisReadOp = 0;
            while ((thisReadOp = is.read(buf)) > -1) {
                baos.write(buf, 0, thisReadOp);
            }
            result = baos.toString();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception ignore) {
                // empty catch block
            }
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
        final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputPath));
        try {
            os.write(rezdata.getBytes());
        } finally {
            try {
                os.close();
            } catch (IOException ignore) {
                // empty catch block
            }
        }
    }

    private boolean hasFileEnding(final String resource) {
        return resource.endsWith(ResourceReader.CONFIG_SUFFIX) || resource.endsWith(ResourceReader.PROP_SUFFIX)
                || resource.endsWith(ResourceReader.XML_SUFFIX) || resource.endsWith(ResourceReader.JS_SUFFIX);
    }

    /**
     * Run from the command line specifying a set of resources to be extracted
     */
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
                System.err.println(args[i] + ": " + iox);
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: scripts/run.sh " + ExtractResource.class.getName() + " [ -o output_directory ] package/to/Resource[.cfg] ...");
    }
}
