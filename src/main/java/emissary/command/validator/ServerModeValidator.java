package emissary.command.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerModeValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ServerModeValidator.class);

    public static void validate(String value) {
        switch (value) {
            case "cluster":
            case "standalone":
                break;
            default:
                LOG.error("Unknown mode: {}", value);
                throw new IllegalArgumentException("Unknown mode: " + value);
        }
    }

    /** This class is not meant to be instantiated. */
    private ServerModeValidator() {}
}
