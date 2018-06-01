package emissary.output.io;

import emissary.output.DropOffUtil;
import emissary.util.io.FileNameGenerator;

public class SimpleFileNameGenerator implements FileNameGenerator {
    private DropOffUtil dropOffUtil;

    /**
     * Creates a file name generator for BUD files
     */
    public SimpleFileNameGenerator() {
        this.dropOffUtil = new DropOffUtil();
    }

    /**
     * {@inheritDoc}
     *
     * @see FileNameGenerator#nextFileName()
     */
    @Override
    public String nextFileName() {
        return dropOffUtil.generateBuildFileName();
    }

}
