package emissary.command.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class ServerModeValidator implements IParameterValidator {
    @Override
    public void validate(String name, String value) throws ParameterException {
        switch (value) {
            case "cluster":
            case "standalone":
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + value);
        }
    }

}
