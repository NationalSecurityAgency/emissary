package emissary.util;

import emissary.config.ConfigUtil;
import emissary.util.io.ResourceReader;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public final class Version {
    private static final Logger logger = LoggerFactory.getLogger(Version.class);

    private String version = "missing version";
    private String timestamp = "missing timestamp";

    /**
     * Build a version object DO NOT call a logger from here
     */
    public Version() {
        readHardWiredConfigInfo();
    }

    /**
     * Read hardwired config info. Cannot be overridden using the normal emissary.config.pkg or emissary.config.dir methods.
     * Does not create a Configurator due to logging restrictions on this method.
     */
    private void readHardWiredConfigInfo() {
        // This configurator is not overridable by the normal config.pkg or config.dir mechanisms
        String rez = Version.class.getName().replace('.', '/') + ConfigUtil.CONFIG_FILE_ENDING;
        try (InputStream rstream = new ResourceReader().getResourceAsStream(rez)) {
            if (rstream != null) {
                for (String line : IOUtils.readLines(rstream, Charset.defaultCharset())) {
                    if (line.startsWith("emissary_version")) {
                        version = getVal(line);
                    } else if (line.startsWith("emissary_build")) {
                        timestamp = getVal(line);
                    }
                }
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    /**
     * Do not call a logger method from here!
     */
    private static String getVal(String line) {
        if (!line.contains(" = ")) {
            return line;
        }
        return line.substring(line.indexOf(" = ") + 3).replaceAll("\"", "");
    }

    @Override
    public String toString() {
        return version + " " + timestamp;
    }

    public String getVersion() {
        return version;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public static void main(String[] args) {
        logger.info("Emissary version {}", new Version());
    }
}
