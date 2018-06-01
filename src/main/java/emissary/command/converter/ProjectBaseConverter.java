package emissary.command.converter;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectBaseConverter extends PathExistsConverter {

    public ProjectBaseConverter(String optionName) {
        super(optionName);
    }

    private static final Logger LOG = LoggerFactory.getLogger(ProjectBaseConverter.class);

    @Override
    public Path convert(String value) {
        String origValue = value;
        String projectBaseEnv = System.getenv("PROJECT_BASE");
        Path projectBaseEnvPath = null;
        String projectBaseEnvString = null;

        // if PROJECT_BASE not null, set some variables
        if (projectBaseEnv != null) {
            projectBaseEnvPath = Paths.get(projectBaseEnv);
            projectBaseEnvString = projectBaseEnvPath.toAbsolutePath().toString();
        }


        // try to use the value from PROJECT_BASE
        if (origValue == null) {
            if (projectBaseEnvString == null) {
                throw new RuntimeException("You set neither PROJECT_BASE nor passed in a directory with -b, --projectBase.  One is required");
            } else {
                value = projectBaseEnvString;
            }
        }

        Path p = super.convert(value);
        String pString = p.toAbsolutePath().toString();

        // both -b and PROJECT_BASE were set, make sure they are pointing to the same place
        if (origValue != null && projectBaseEnv != null && !projectBaseEnvString.equals(pString)) {
            String msg = "You passed in " + projectBaseEnvString + " but PROJECT_BASE was set to " + pString;
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
        return p;
    }
}
