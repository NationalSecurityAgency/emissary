package emissary.command.converter;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PathExistsReadableConverter extends PathExistsConverter {

    public PathExistsReadableConverter(String optionName) {
        super(optionName);
    }

    private static final Logger LOG = LoggerFactory.getLogger(PathExistsReadableConverter.class);

    @Override
    public Path convert(String value) {
        Path p = super.convert(value);
        if (!Files.isReadable(p)) {
            String msg = String.format("The option '%s' was configured with path '%s' which is not readable", getOptionName(), p);
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
        return p;
    }
}
