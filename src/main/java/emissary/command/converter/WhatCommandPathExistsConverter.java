package emissary.command.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class WhatCommandPathExistsConverter extends PathExistsReadableConverter {
    private static final Logger LOG = LoggerFactory.getLogger(WhatCommandPathExistsConverter.class);

    @Override
    public Path convert(String value) {
        Path p = super.convert("-i", value);
        if (!Files.isReadable(p)) {
            String msg = String.format("The option '-i' was configured with path '%s' which is not readable", p);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
        return p;
    }
}
