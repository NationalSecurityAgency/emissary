package emissary.output.io;

import emissary.directory.EmissaryNode;
import emissary.util.io.FileNameGenerator;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Creates a Filename Generator that contains a timestamp (julian day), uuid, node name/host, and filter name. Example
 * filename with a filter param of json is: 20232231650_7959b045-d895-4b34-bed2-8800b5071dcd_localhost_json
 */
public class DateFilterFilenameGenerator implements FileNameGenerator {

    protected static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyDDDHHmm", Locale.getDefault());
    public static final char DELIMITER = '_';
    public static final char DASH = '-';
    private final String filterNamePart;
    private static final String NODE_NAME = System.getProperty(EmissaryNode.NODE_NAME_PROPERTY);

    /**
     * Create a file name generator that contains date, uuid, node name, filter extension.
     * 
     * @param filterName filter name used to create file extension
     */
    public DateFilterFilenameGenerator(String filterName) {
        this.filterNamePart = (StringUtils.isNotBlank(filterName) ? DELIMITER + filterName.replace(DELIMITER, DASH) : StringUtils.EMPTY);
    }

    /**
     *
     * @return the next unique filename from this generator
     */
    @Override
    public String nextFileName() {
        return createFileName(filterNamePart);
    }

    public static String createFileName(String extension) {
        return String.format("%s%s%s%s%s%s", now(), DELIMITER, UUID.randomUUID(), DELIMITER, NODE_NAME, extension);
    }

    private static String now() {
        return DATE_PATTERN.format(LocalDateTime.now(ZoneId.systemDefault()));
    }

}
