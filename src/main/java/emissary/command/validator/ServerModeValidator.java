package emissary.command.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerModeValidator implements IParameterValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ServerModeValidator.class);

    @Override
    public void validate(String name, String value) throws ParameterException {
        switch (value) {
            case "cluster":
            case "standalone":
                break;
            default:
                LOG.error("Unknown mode: {}", value);
                throw new IllegalArgumentException("Unknown mode: " + value);
        }
    }

}
