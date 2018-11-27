package emissary.output;

import com.esotericsoftware.kryo.io.Output;
import com.google.common.annotations.VisibleForTesting;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.core.kryo.EmissaryKryoFactory;
import emissary.output.filter.FilterUtil;
import emissary.output.filter.IDropOffFilter;
import emissary.output.roller.JournaledCoalescer;
import emissary.output.roller.journal.KeyedOutput;
import emissary.place.EmptyFormPlace;
import emissary.place.ServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.roll.RollManager;
import emissary.roll.Roller;
import emissary.util.io.FileNameGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static emissary.roll.Roller.CFG_ROLL_INTERVAL;

/**
 * DropOff to generate serialized IBDOs.
 */
public class KryoDropOffPlace extends ServiceProviderPlace implements EmptyFormPlace, FileNameGenerator {
    protected static final String MAX_OUTPUT_APPENDERS = "MAX_OUTPUT_APPENDERS";
    protected static final String CFG_DIE_ON_ERROR = "SYSTEM_EXIT_ON_EXCEPTION";
    private static final String DEFAULT_PATH = "/localoutput/kryo";
    public static final String CONTENT_URI = "CONTENT_URI";
    public static final String MAX_ROLL_FILE_SIZE = "MAX_FILE_SIZE";

    private final ALThreadLocal lists = new ALThreadLocal();
    protected JournaledCoalescer journaledCoalescer;
    protected final EmissaryKryoFactory kryos = new EmissaryKryoFactory();
    protected int maxOutputAppenders;
    protected int interval = 5;
    protected int maxRollFileSize = 250 * 1024 * 1024;
    protected Path outputPath;
    protected boolean dieOnIOEx;

    public KryoDropOffPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    public KryoDropOffPlace(final Configurator configInfo) throws IOException {
        this.configG = configInfo;
        configurePlace();
    }

    @VisibleForTesting
    protected void configurePlace() {
        outputPath = Paths.get(configG.findStringEntry(IDropOffFilter.OUTPUT_PATH, ConfigUtil.getProjectBase() + DEFAULT_PATH));
        maxOutputAppenders = configG.findIntEntry(MAX_OUTPUT_APPENDERS, AgentPool.computePoolSize());
        interval = configG.findIntEntry(CFG_ROLL_INTERVAL, interval);
        dieOnIOEx = configG.findBooleanEntry(CFG_DIE_ON_ERROR, false);
        maxRollFileSize = (int) configG.findSizeEntry(MAX_ROLL_FILE_SIZE, maxRollFileSize);

        // TODO: max roll file size

        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            logger.error("Unable to create directory for Kryo output, exiting immediately. ", e);
            System.exit(1);
        }

        try {
            journaledCoalescer = getCoalescer(outputPath, maxOutputAppenders);
        } catch (IOException | InterruptedException ex) {
            logger.error("Unable to instantiate RollableFileOutputStream for writing", ex);
            System.exit(1);
        }

        Roller r = new Roller(this.maxRollFileSize, TimeUnit.MINUTES, interval, journaledCoalescer);
        RollManager.getManager().addRoller(r);
        logger.debug("Added Roller for KryoDropOff running every {} minutes.", interval);
    }

    protected JournaledCoalescer getCoalescer(Path outputPath, int maxOutputAppenders) throws InterruptedException, IOException {
        JournaledCoalescer jc = new JournaledCoalescer(outputPath, this, maxOutputAppenders);
        return jc;
    }

    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(final List<IBaseDataObject> payloadList) throws Exception {

        final ArrayList<IBaseDataObject> l = prep(payloadList);

        try (KeyedOutput ko = journaledCoalescer.getOutput()) {
            Output output = new Output(ko);
            l.get(0).putParameter(CONTENT_URI + "_KRYO", "file://" + ko.getFinalDestination().toString());
            kryos.get().writeObject(output, l);
            output.flush();
            ko.commit();
        } catch (IOException ex) {
            logger.error("IOException during KryoDropOff", ex);
            if (dieOnIOEx) {
                logger.error("FATAL: Unable to write IBDO. Forcing system shutdown");
                System.exit(1);
            }
        } finally {
            l.clear();
        }

        for (IBaseDataObject ibdo : payloadList) {
            nukeMyProxies(ibdo);
        }
        return Collections.EMPTY_LIST;
    }

    protected ArrayList<IBaseDataObject> prep(final List<IBaseDataObject> payloadList) {
        // the provided list is a Collections.synchronized wrapped list which is not registered with kryo
        final ArrayList<IBaseDataObject> l = lists.get();
        l.clear();
        for (IBaseDataObject ibdo : payloadList) {
            FilterUtil.processData(ibdo, Collections.emptySet(), getDirectoryEntry());
            l.add(ibdo);
        }
        return l;
    }

    @Override
    public String nextFileName() {
        return UUID.randomUUID().toString() + ".kryo";
    }

    // here to limit object creation and GC
    private static class ALThreadLocal extends ThreadLocal<ArrayList<IBaseDataObject>> {

        @Override
        protected ArrayList<IBaseDataObject> initialValue() {
            return new ArrayList<>();
        }

    }

}
