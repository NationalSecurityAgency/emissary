package emissary.command.converter;

import emissary.pickup.Priority;
import emissary.pickup.PriorityDirectory;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.ITypeConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriorityDirectoryConverter implements ITypeConverter<PriorityDirectory> {

    public PriorityDirectoryConverter() {
        super();
    }

    public static final String PRIORITY_DIR_REGEX = ".*:\\d+$";
    private static final Pattern priorityDirRegex = Pattern.compile(PRIORITY_DIR_REGEX);

    @Override
    public PriorityDirectory convert(String value) {
        final String dirName;
        final int priority;
        Matcher matcher = priorityDirRegex.matcher(value);
        if (matcher.matches()) {
            dirName = StringUtils.substringBeforeLast(value, ":");
            priority = Integer.parseInt(StringUtils.substringAfterLast(value, ":"));
        } else {
            dirName = value;
            priority = Priority.DEFAULT;
        }
        return new PriorityDirectory(StringUtils.appendIfMissing(dirName, "/"), priority);
    }
}
