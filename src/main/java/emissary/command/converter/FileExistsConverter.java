package emissary.command.converter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.beust.jcommander.converters.BaseConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileExistsConverter extends BaseConverter<File> {

    private static final Logger LOG = LoggerFactory.getLogger(FileExistsConverter.class);

    public FileExistsConverter(String optionName) {
        super(optionName);
    }

    @Override
    public File convert(String value) {
        Path p = Paths.get(value);
        if (!Files.exists(p)) {
            String msg = String.format("The option '%s' was configured with path '%s' which does not exist", getOptionName(), p);
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
        return p.toFile();
    }
}
