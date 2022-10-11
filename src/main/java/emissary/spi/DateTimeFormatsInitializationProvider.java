package emissary.spi;

import emissary.util.DateTimeFormats;

public class DateTimeFormatsInitializationProvider implements InitializationProvider {

    @Override
    public void initialize() {
        DateTimeFormats.initialize();
    }
}
