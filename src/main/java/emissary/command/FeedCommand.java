package emissary.command;

import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import emissary.command.converter.PriorityDirectoryConverter;
import emissary.command.converter.WorkspaceSortModeConverter;
import emissary.pickup.PriorityDirectory;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Start the feeder process given a particular WorkSpace implemetation to distribute work to peer nodes")
public class FeedCommand extends ServiceCommand {

    public static String COMMAND_NAME = "feed";

    public String getCommandName() {
        return COMMAND_NAME;
    }

    public static int DEFAULT_PORT = 7001;

    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    private static final Logger LOG = LoggerFactory.getLogger(FeedCommand.class);

    @Parameter(names = {"-w", "--workspace"}, description = "fully qualified class to use as the WorkSpace implementation")
    private String workspaceClass = "emissary.pickup.WorkSpace";

    @Parameter(names = {"--bundleSize"}, description = "number of files to pack in each work bundle given to the peers")
    private int bundleSize = 1;

    @Parameter(names = {"-ci", "--caseId"}, description = "case id to assign")
    private String caseId = "auto";

    @Parameter(names = {"-cc", "--caseClass"}, description = "case class to assign")
    private String caseClass = "";

    @Parameter(names = {"-ep", "--eatPrefix"}, description = "prefix to eat on input files when creating work bundles")
    private String eatPrefix = "";

    @Parameter(names = {"-cs", "--case"}, description = "Pattern to use to find the clients in the namespace")
    private String clientPattern = "INITIAL.FILE_PICK_UP_CLIENT.INPUT.*";

    @Parameter(names = {"-o", "--feedOutputRoot"},
            description = "the root path to use when writing successfully parsed input, defaults to projectBase/DoneParsedData")
    private String feedOutputRoot;

    @Parameter(names = {"-i", "--inputRoot"},
            description = "the root path or comma-separated paths to use when reading input, can use PriorityDirectory format",
            converter = PriorityDirectoryConverter.class)
    private List<PriorityDirectory> priorityDirectories;

    @Parameter(names = {"--sort"}, description = "order which to sort files as they are put into work bundles, defaults to Priority sort (10)",
            converter = WorkspaceSortModeConverter.class)
    private Comparator<WorkBundle> sort;

    @Parameter(names = {"-ns", "--namespaceName"}, description = "name to assign to the work space")
    private String workspaceName = "WorkSpace";

    @Parameter(names = {"-sd", "--skipDot"}, description = "skips dot files when creating work bundles")
    private boolean skipDotFile = true;

    @Parameter(names = {"-dirs", "--includeDirs"},
            description = "Set directory processing flag. When true directory entries are retrieved from the input area just like normal")
    private boolean includeDirs = false;

    @Parameter(names = {"-l", "--loop"}, description = "Controls loop functionality of workspace")
    private boolean loop = true;

    @Parameter(names = {"-r", "--retry"}, description = "controls if we retry or not")
    private boolean retry = true;

    @Parameter(names = {"--simple"}, description = "turn on simple mode")
    private boolean simple = false;

    @Parameter(names = {"-ft", "--fileTimestamp"},
            description = "set the use of file timestamps to control whether a file is new enough to be added to the queue")
    private boolean fileTimestamp = false;

    @Override
    public void startService() {
        if (CollectionUtils.isEmpty(priorityDirectories)) {
            LOG.error("No input root or priority directories specified");
            throw new ParameterException("Missing required parameter '-i' for input root or priority directories");
        }

        LOG.info("Starting feeder using {} as the workspace class", workspaceClass);
        try {
            WorkSpace ws = (WorkSpace) Class.forName(workspaceClass).getConstructor(FeedCommand.class).newInstance(this);
            ws.run();
        } catch (Exception e) {
            LOG.error("Error running WorkSpace class: {} ", workspaceClass, e);
        }
    }

    @Override
    public void setupCommand() {
        setupHttp();
        setupFeed();
        reinitLogback();
    }

    public void setupFeed() {
        String flavorMode = "CLUSTER";
        if (getFlavor() != null) {
            flavorMode = flavorMode + "," + getFlavor();
        }

        // Must maintain insertion order
        Set<String> flavorSet = new LinkedHashSet<>();
        for (String f : flavorMode.split(",")) {
            flavorSet.add(f.toUpperCase());
        }

        overrideFlavor(String.join(",", flavorSet));
    }

    public String getWorkspaceClass() {
        return workspaceClass;
    }

    public boolean isSimple() {
        return simple;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getCaseClass() {
        return caseClass;
    }

    public String getEatPrefix() {
        return eatPrefix;
    }

    public boolean isSkipDotFile() {
        return skipDotFile;
    }

    public boolean isIncludeDirs() {
        return includeDirs;
    }

    public String getClientPattern() {
        return clientPattern;
    }

    public int getBundleSize() {
        return bundleSize;
    }

    public String getOutputRoot() {
        if (this.feedOutputRoot == null) {
            this.feedOutputRoot = Paths.get(this.getProjectBase().toString(), "DoneParsedData").toString();
        }
        return this.feedOutputRoot;
    }

    public List<PriorityDirectory> getPriorityDirectories() {
        return priorityDirectories;
    }

    public Comparator<WorkBundle> getSort() {
        return sort;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public boolean isLoop() {
        return loop;
    }

    public boolean isRetry() {
        return retry;
    }

    public boolean isFileTimestamp() {
        return fileTimestamp;
    }

}
