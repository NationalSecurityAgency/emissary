package emissary.command.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.beust.jcommander.converters.BaseConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PathExistsConverter extends BaseConverter<Path> {

    public PathExistsConverter(String optionName) {
        super(optionName);
    }

    private static final Logger LOG = LoggerFactory.getLogger(PathExistsConverter.class);

    @Override
    public Path convert(String value) {
        Path p = null;
        if (value.endsWith("/")) {
            p = Paths.get(value.substring(0, value.length() - 1));
        } else {
            p = Paths.get(value);
        }
        // ensure the value exists
        if (!Files.exists(p)) {
            String msg = String.format("The option '%s' was configured with path '%s' which does not exist", getOptionName(), p);
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
        return p;
    }


}
