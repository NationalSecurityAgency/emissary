package emissary.output.io;

import emissary.directory.EmissaryNode;
import emissary.util.io.FileNameGenerator;

import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class DateFilterFilenameGenerator implements FileNameGenerator {

    protected static final SimpleDateFormat DATE_PATTERN = new SimpleDateFormat("yyyyDDDHHmm");
    public static final char DELIMITER = '_';
    public static final char DASH = '-';
    private final String filterNamePart;
    private static final String NODE_NAME = System.getProperty(EmissaryNode.NODE_NAME_PROPERTY);

    /**
     * Create a file name generator that contains date,
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
        return DATE_PATTERN.format(new Date());
    }

}
