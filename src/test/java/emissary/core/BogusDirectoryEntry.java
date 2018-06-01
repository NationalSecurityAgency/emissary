package emissary.core;

import emissary.directory.DirectoryEntry;

/**
 * Fake directory entry, isLocal always false
 */
public class BogusDirectoryEntry extends DirectoryEntry {
    static final long serialVersionUID = 5246049552248752866L;

    /**
     * Copy constructor
     * 
     * @param that the entry to copy
     */
    public BogusDirectoryEntry(final DirectoryEntry that) {
        super(that);
    }

    /**
     * Always false
     */
    @Override
    public boolean isLocal() {
        return false;
    }
}
