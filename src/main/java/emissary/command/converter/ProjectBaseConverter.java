package emissary.command.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ITypeConverter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectBaseConverter extends PathExistsConverter implements ITypeConverter<Path> {
    public ProjectBaseConverter() {
        this(null);
    }

    public ProjectBaseConverter(String optionName) {
        super(optionName);
    }

    private static final Logger LOG = LoggerFactory.getLogger(ProjectBaseConverter.class);

    @Override
    public Path convert(String value) {
        String origValue = value;
        String projectBaseEnv = System.getenv("PROJECT_BASE");
        Path projectBaseEnvPath;
        String projectBaseEnvString = null;

        // if PROJECT_BASE not null, set some variables
        if (projectBaseEnv != null) {
            projectBaseEnvPath = Paths.get(projectBaseEnv);
            projectBaseEnvString = projectBaseEnvPath.toAbsolutePath().toString();
        }


        // try to use the value from PROJECT_BASE
        if (origValue == null) {
            if (projectBaseEnvString == null) {
                throw new IllegalArgumentException("You set neither PROJECT_BASE nor passed in a directory with -b, --projectBase.  One is required");
            } else {
                value = projectBaseEnvString;
            }
        }

        Path p = super.convert("-b", value);
        String pString = p.toAbsolutePath().toString();

        // both -b and PROJECT_BASE were set, make sure they are pointing to the same place
        if (origValue != null && projectBaseEnv != null && !projectBaseEnvString.equals(pString)) {
            String msg = "You passed in " + pString + " but PROJECT_BASE was set to " + projectBaseEnvString;
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
        return p;
    }
}
