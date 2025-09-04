package emissary.command.converter;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ITypeConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathExistsConverter implements ITypeConverter<Path> {
    private final String optionName;

    public PathExistsConverter() {
        this(null);
    }

    public PathExistsConverter(@Nullable String optionName) {
        this.optionName = optionName;
    }

    private static final Logger LOG = LoggerFactory.getLogger(PathExistsConverter.class);

    @Override
    public Path convert(String value) {
        return convert(optionName, value);
    }

    public Path convert(String option, String value) {
        Path p = Paths.get(Strings.CS.removeEnd(value, "/"));
        // ensure the value exists
        if (!Files.exists(p)) {
            String msg = String.format("The option '%s' was configured with path '%s' which does not exist", option, p);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
        return p;
    }

    public String getOptionName() {
        return optionName;
    }

}
