package emissary.id;

import java.io.File;
import java.io.IOException;
import java.util.*;

import emissary.core.IBaseDataObject;
import emissary.util.UnixFile;

/**
 * Accesses emissary.util.UnixFile to perform file identification tests using emissary.util.UnixFile
 */
public class UnixFilePlace extends emissary.id.IdPlace {

    protected boolean chop = true;
    protected boolean replace = false;
    protected boolean replaceFiletype = false;
    protected boolean upcase = false;
    protected boolean removeCommas = false;
    protected boolean swallowIgnorableExceptions = false;
    protected Set<String> chopAtTwo = new HashSet<>();
    protected Map<String, Integer> minSizeMap = new HashMap<>();

    /**
     * The {@link UnixFile} instance
     */
    protected UnixFile unixFileUtil = null;

    /**
     * The remote constructorn
     */
    public UnixFilePlace(final String cfgInfo, final String dir, final String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * The static standalone (test) constructor. The cfgInfo argument is an absolute path to some configuration file.
     */
    public UnixFilePlace(final String cfgInfo) throws IOException {
        super(cfgInfo, "TestUnixFilePlace.foo.com:8003");
        configurePlace();
    }

    public UnixFilePlace() throws IOException {
        configurePlace();
    }

    /**
     * Set up place specific information from the config file, load JNI, etc.
     */
    protected void configurePlace() throws IOException {
        final List<String> magicPaths = configG.findEntries("MAGIC_FILE", "magic");
        for (final String mPath : magicPaths) {
            final File mfile = new File(mPath);
            if (!mfile.exists() || !mfile.canRead()) {
                throw new IOException("Missing or unreadable MAGIC_FILE " + mPath);
            }
        }
        this.swallowIgnorableExceptions = configG.findBooleanEntry("SWALLOW_IGNORABLE_EXCEPTIONS", this.swallowIgnorableExceptions);
        this.unixFileUtil = new UnixFile(magicPaths, this.swallowIgnorableExceptions);
        logger.debug(
                "Created unixFile with " + magicPaths.size() + " magic files containing " + this.unixFileUtil.magicEntryCount() + " magic rules");

        this.chop = configG.findBooleanEntry("CHOP", this.chop);
        this.chopAtTwo = configG.findEntriesAsSet("CHOP_AT_TWO");
        this.replace = configG.findBooleanEntry("REPLACE", this.replace);
        this.upcase = configG.findBooleanEntry("UPCASE", this.upcase);
        this.removeCommas = configG.findBooleanEntry("REMOVE_COMMAS", this.removeCommas);
        this.replaceFiletype = configG.findBooleanEntry("REPLACE_FILETYPE", this.replaceFiletype);
        for (final Map.Entry<String, String> entry : configG.findStringMatchMap("MIN_SIZE_").entrySet()) {
            try {
                this.minSizeMap.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            } catch (NumberFormatException ex) {
                logger.info("Must be numeric MIN_SIZE_" + entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    /**
     * Consume a DataObject, and return a transformed one.
     */
    @Override
    public void process(final IBaseDataObject d) {
        final byte[] bytes = d.data();

        // Bail out on empty data
        if (bytes == null || bytes.length == 0) {
            d.setCurrentForm(emissary.core.Form.EMPTY);
            d.setFileType(emissary.core.Form.EMPTY);
            return;
        }

        try {
            String currentForm = this.unixFileUtil.evaluateByMagicNumber(bytes);
            if (currentForm != null) {
                if (this.chop && currentForm.indexOf(" ") > 0) {
                    String firstSubstring = currentForm.substring(0, currentForm.indexOf(" "));
                    // chop some things at 2
                    if (this.chopAtTwo.contains(firstSubstring)) {
                        String[] parts = currentForm.split(" ");
                        currentForm = parts[0] + " " + parts[1];
                    } else {
                        currentForm = firstSubstring;
                    }
                }

                if (this.replace && currentForm.indexOf(" ") > -1) {
                    currentForm = currentForm.replaceAll(" ", "_");
                }

                if (this.upcase && currentForm.length() > 0) {
                    currentForm = currentForm.toUpperCase();
                }

                if (this.removeCommas && currentForm.indexOf(",") > 0) {
                    currentForm = currentForm.replaceAll("", "_");
                }

                if (this.minSizeMap.containsKey(currentForm) && (bytes.length < this.minSizeMap.get(currentForm))) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Type " + currentForm + " does not meet min size requirement " + this.minSizeMap.get(currentForm) + " < "
                                + bytes.length);
                    }
                } else {
                    d.setCurrentForm(currentForm);
                    if (replaceFiletype) {
                        d.setFileType(currentForm);
                    } else {
                        d.setFileTypeIfEmpty(currentForm);
                    }
                }
            } else {
                logger.debug("Unixfile result was null");
            }
        } catch (Exception e) {
            logger.error("Could not run unixfile", e);
            d.addProcessingError(e.getMessage());
            return;
        }
        return;
    }

    public static void main(final String[] args) {
        mainRunner(UnixFilePlace.class.getName(), args);
    }
}
