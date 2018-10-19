package emissary.command.converter;

import com.beust.jcommander.IStringConverter;
import emissary.pickup.Priority;
import emissary.pickup.PriorityDirectory;

public class PriorityDirectoryConverter implements IStringConverter<PriorityDirectory> {
    public static final String PRIORITY_DIR_REGEX = ".*:[0-9]+$";

    @Override
    public PriorityDirectory convert(String value) {
        // Take from the old WorkSpace.java class
        if (value.matches(PRIORITY_DIR_REGEX)) {
            final int pos = value.lastIndexOf(":");
            String dirName = value.substring(0, pos);
            if (!dirName.endsWith("/")) {
                dirName += "/";
            }
            final int priority = Integer.parseInt(value.substring(pos + 1));
            return new PriorityDirectory(dirName, priority);
        } else {
            if (!value.endsWith("/")) {
                value += "/";
            }
            return new PriorityDirectory(value, Priority.DEFAULT);
        }
    }
}
