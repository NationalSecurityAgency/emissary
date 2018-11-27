package emissary.output.filter;

import com.esotericsoftware.kryo.KryoException;
import com.google.common.annotations.VisibleForTesting;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.core.kryo.BDODeserialzerIterator;
import emissary.output.DropOffUtil;
import emissary.output.push.Bucket;
import emissary.roll.Rollable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class KryoBDOFilter implements Rollable {
    protected static final Logger logger = LoggerFactory.getLogger(KryoBDOFilter.class);

    private final ReentrantLock outputLock = new ReentrantLock();
    private final HashMap<String, OutputStream> outputs = new HashMap<>();

    @VisibleForTesting
    protected DropOffUtil dropOffUtil;
    protected FileSystem fs;
    protected Path outputPath;
    protected Path errorPath;
    protected String inputPath;
    protected Bucket kryoBucket;
    protected List<IDropOffFilter> filters = new ArrayList<>();


    public KryoBDOFilter() throws IOException {
        fs = FileSystems.getDefault();
        configure(ConfigUtil.getConfigInfo(this.getClass()));
        validate();
    }

    public void configure(final Configurator configG) throws IOException {
        inputPath = configG.findStringEntry("INPUT_PATH", ConfigUtil.getProjectBase() + "/localoutput/kryo");
        outputPath = Paths.get(configG.findStringEntry("OUTPUT_PATH", ConfigUtil.getProjectBase() + "/localoutput/kryo-rolled"));
        errorPath = Paths.get(configG.findStringEntry("ERROR_PATH", ConfigUtil.getProjectBase() + "/localoutput/kryo-error"));

        createDirectories(this.outputPath);
        createDirectories(this.errorPath);

        dropOffUtil = new DropOffUtil(configG);
        List<String> filterClasses = configG.findEntries("OUTPUT_FILTER");
        initializeFilters(filterClasses, configG);

        final PathMatcher pm = fs.getPathMatcher("regex:^[0-9a-z\\-\\.]{36}\\.kryo$");
        kryoBucket = new Bucket("KRYO", fs.getPath(inputPath), pm);
    }

    @VisibleForTesting
    protected void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            logger.error("Unable to create directory for KRYO output, exiting immediately. ", e);
            System.exit(1);
        }
    }

    @Override
    public void roll() {
        Path p = null;
        try (DirectoryStream<Path> d = Files.newDirectoryStream(kryoBucket.getSource(), kryoBucket.getStreamFilter())) {
            for (Path path : d) {
                try {
                    String trimmedExtension = trimKryoExtension(path.getFileName());
                    setupNewOutputs(trimmedExtension);
                    // copy path name in case we need it for an exception
                    p = path;
                    runFilters(path);
                    Path dest = outputPath.resolve(path.getFileName());
                    closeFilters();
                    Files.move(path, dest);
                } catch (KryoException ke) {
                    logger.error("Kryo Deserialization error.", ke);
                } finally {
                    if (!Files.exists(path)) {
                        continue;
                    }

                    logger.error("Error running filters. KRYO file {} was not moved to the rolled directory. Moving to the error directory.",
                            path.toString());
                    Path dest = errorPath.resolve(path.getFileName());
                    if (!Files.exists(dest)) {
                        Files.move(path, dest);
                    } else {
                        logger.error("File already exists in the error directory. removing {} file", dest.toString());
                        Files.delete(dest);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Unable to push from bucket " + kryoBucket + ". Attempted path " + p + ". Will try again.", e);
        }
    }

    public void runFilters(Path p) throws KryoException {

        BDODeserialzerIterator it = getBDODeserializer(p);
        while (it.hasNext()) {
            List<IBaseDataObject> tree = it.next();

            // start transaction
            try {
                outputLock.lock();
                final Map<String, Object> filterParams = FilterUtil.preFilter(tree, dropOffUtil);
                if (tree.isEmpty()) {
                    continue;
                }

                tree.get(0).setParameter("KRYO_FILE", p.getFileName());

                for (IDropOffFilter filter : filters) {
                    if (!filter.isOutputtable(tree)) {
                        logger.warn("IBDO not outputtable. IBDO: {}", tree.get(0).shortName());
                        continue;
                    }

                    OutputStream output = outputs.get(filter.getFilterName());
                    int status = filter.filter(tree, filterParams, output);
                    if (status != IDropOffFilter.STATUS_SUCCESS) {
                        logger.error("Drop off filter {} encountered an error writing out {}. KryoFile: {}", filter.getFilterName(), tree.get(0)
                                .shortName(), p.getFileName());
                    }
                }
            } catch (Exception e) {
                logger.error("Error writing file", e);
            } finally {
                outputLock.unlock();
            }
            // end transaction
        }
    }

    private BDODeserialzerIterator getBDODeserializer(Path p) throws KryoException {
        try {
            return new BDODeserialzerIterator(p);
        } catch (IOException ioe) {
            logger.error("Error deserializing Kryo file {}", p.toString(), ioe);
            throw new KryoException("Deserialization error");
        }
    }

    private void closeFilters() {
        try {
            for (IDropOffFilter filter : filters) {
                if (filter.getOutputPath() == null) {
                    logger.error("Filter output path does not exist. This should not be possible. Shutdown and fix. Filter: {}",
                            filter.getFilterName());
                    continue;
                }
                outputs.get(filter.getFilterName()).close();
            }
        } catch (Exception ioe) {
            logger.warn("Error closing JournaledChannel", ioe);
        }
    }

    private String trimKryoExtension(Path fileName) {
        String fileString = fileName.toString();
        return fileString.substring(0, fileString.indexOf(".kryo"));
    }

    @Override
    public boolean isRolling() {
        return isLocked();
    }

    @Override
    public void close() {
        roll();
    }

    protected boolean isLocked() {
        return outputLock.isLocked();
    }

    public void setupNewOutputs(String fileNameWithoutExtension) {
        try {
            outputLock.lock();
            for (IDropOffFilter filter : filters) {
                String filteredFileName = fileNameWithoutExtension + filter.getFileExtension();
                if (filter.hasFileNameGenerator()) {
                    filteredFileName = filter.getFileNameGenerator().nextFileName();
                }
                Path finalPath = filter.getOutputPath().resolve(filteredFileName);
                outputs.put(filter.getFilterName(), Files.newOutputStream(finalPath));
            }
        } catch (IOException ioe) {
            logger.warn("Error setting up new output dir for Kryo file:", fileNameWithoutExtension, ioe);
        } finally {
            outputLock.unlock();
        }
    }

    /**
     * Start up the requested filter
     *
     * @param filterClasses the name:class values of the configured filter for this drop off
     */
    protected void initializeFilters(final List<String> filterClasses, final Configurator configG) {
        filters.addAll(FilterUtil.initializeFilters(filterClasses, configG));
    }

    @VisibleForTesting
    protected void validate() {
        if (fs == null) {
            throw new IllegalArgumentException("FileSystem cannot be null");
        }
        if (kryoBucket == null) {
            throw new IllegalStateException("Outbound directory map must have at least one entry");
        }
        if (dropOffUtil == null) {
            throw new IllegalStateException("DropOffUtil cannot be null");
        }
        if (filters.isEmpty()) {
            throw new IllegalStateException("Filters list cannot be empty");
        }
        if (outputPath == null) {
            throw new IllegalStateException("Output dir cannot be empty");
        }
        if (errorPath == null) {
            throw new IllegalStateException("Error dir cannot be empty.");
        }
        if (inputPath == null) {
            throw new IllegalStateException("Input path cannot be empty.");
        }
    }
}
