package emissary.output.io;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import emissary.directory.EmissaryNode;
import emissary.util.TimeUtil;
import emissary.util.io.FileNameGenerator;
import org.apache.commons.lang.StringUtils;

/**
 * Create a filename generator that uses a datestamp as the name
 */
public class DateStampFilenameGenerator implements FileNameGenerator {

    // the date/time pattern
    protected final DateTimeFormatter formatter;

    // unique identifier to add to the file
    protected final String identifier;

    // file suffix/extension
    protected final String fileSuffix;

    // track last file name
    protected String lastFileName = "";

    // add a sequence number to the file
    protected int seq = 0;

    public DateStampFilenameGenerator() {
        this("");
    }

    public DateStampFilenameGenerator(String fileSuffix) {
        this(fileSuffix, System.getProperty(EmissaryNode.NODE_NAME_PROPERTY));
    }

    public DateStampFilenameGenerator(String fileSuffix, String identifier) {
        this(fileSuffix, identifier, "yyyyMMddHHmm");
    }

    public DateStampFilenameGenerator(String fileSuffix, String identifier, String datePattern) {
        this.fileSuffix = fileSuffix;
        this.identifier = identifier;
        this.formatter = DateTimeFormatter.ofPattern(datePattern).withZone(TimeUtil.getTimezone());
    }

    @Override
    public String nextFileName() {
        String dateFileName = formatter.format(Instant.now());
        seq = StringUtils.startsWith(lastFileName, dateFileName) ? seq + 1 : 0;
        lastFileName = dateFileName;
        return dateFileName + String.format("%03d", seq) + identifier + fileSuffix;
    }
}
