package emissary.command.converter;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ITypeConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileExistsConverter implements ITypeConverter<File> {
    private final String optionName;
    private static final Logger LOG = LoggerFactory.getLogger(FileExistsConverter.class);

    public FileExistsConverter() {
        this(null);
    }

    public FileExistsConverter(@Nullable String optionName) {
        this.optionName = optionName;
    }

    @Override
    public File convert(String value) {
        Path p = Paths.get(value);
        if (!Files.exists(p)) {
            String msg = String.format("The option '%s' was configured with path '%s' which does not exist", optionName, p);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
        return p.toFile();
    }
}
