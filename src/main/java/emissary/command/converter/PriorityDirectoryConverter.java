package emissary.command.converter;

import emissary.pickup.Priority;
import emissary.pickup.PriorityDirectory;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.ITypeConverter;

public class PriorityDirectoryConverter implements ITypeConverter<PriorityDirectory> {

    public PriorityDirectoryConverter() {
        super();
    }

    public static final String PRIORITY_DIR_REGEX = ".*:\\d+$";

    @Override
    public PriorityDirectory convert(String value) {
        final String dirName;
        final int priority;
        if (value.matches(PRIORITY_DIR_REGEX)) {
            dirName = StringUtils.substringBeforeLast(value, ":");
            priority = Integer.parseInt(StringUtils.substringAfterLast(value, ":"));
        } else {
            dirName = value;
            priority = Priority.DEFAULT;
        }
        return new PriorityDirectory(StringUtils.appendIfMissing(dirName, "/"), priority);
    }
}
