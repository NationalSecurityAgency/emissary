package emissary.command;

import emissary.command.converter.PriorityDirectoryConverter;
import emissary.command.converter.WorkspaceSortModeConverter;
import emissary.pickup.PriorityDirectory;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Command(description = "Start the feeder process given a particular WorkSpace implementation to distribute work to peer nodes",
        subcommands = {HelpCommand.class})
public class FeedCommand extends ServiceCommand {
    @Spec
    private CommandSpec spec;
    private static final Logger LOG = LoggerFactory.getLogger(FeedCommand.class);

    public static final String COMMAND_NAME = "feed";
    public static final int DEFAULT_PORT = 7001;

    @Option(names = {"-w", "--workspace"}, description = "fully qualified class to use as the WorkSpace implementation\nDefault: ${DEFAULT-VALUE}")
    private String workspaceClass = "emissary.pickup.WorkSpace";

    @Option(names = {"--bundleSize"}, description = "number of files to pack in each work bundle given to the peers\nDefault: ${DEFAULT-VALUE}")
    private int bundleSize = 1;

    @Option(names = {"-ci", "--caseId"}, description = "case id to assign\nDefault: ${DEFAULT-VALUE}")
    private String caseId = "auto";

    @Option(names = {"-cc", "--caseClass"}, description = "case class to assign\nDefault: <empty string>")
    private String caseClass = "";

    @Option(names = {"-ep", "--eatPrefix"}, description = "prefix to eat on input files when creating work bundles\nDefault: <empty string>")
    private String eatPrefix = "";

    @Option(names = {"-cs", "--case"}, description = "Pattern to use to find the clients in the namespace\nDefault: ${DEFAULT-VALUE}")
    private String clientPattern = "INITIAL.FILE_PICK_UP_CLIENT.INPUT.*";

    @Option(names = {"-o", "--feedOutputRoot"},
            description = "the root path to use when writing successfully parsed input, defaults to projectBase/DoneParsedData")
    private String feedOutputRoot;

    @Option(names = {"-i", "--inputRoot"},
            split = ",",
            description = "the root path or comma-separated paths to use when reading input, can use PriorityDirectory format",
            converter = PriorityDirectoryConverter.class)
    private List<PriorityDirectory> priorityDirectories;

    @Option(names = {"--sort"}, description = "order which to sort files as they are put into work bundles, defaults to Priority sort (10)",
            converter = WorkspaceSortModeConverter.class)
    private Comparator<WorkBundle> sort;

    @Option(names = {"-ns", "--namespaceName"}, description = "name to assign to the work space\nDefault: ${DEFAULT-VALUE}")
    private String workspaceName = "WorkSpace";

    @Option(names = {"-sd", "--skipDot"}, description = "skips dot files when creating work bundles\nDefault: ${DEFAULT-VALUE}")
    private boolean skipDotFile = true;

    @Option(names = {"-dirs", "--includeDirs"},
            description = "Set directory processing flag. When true directory entries are retrieved from the input area just like normal\nDefault: ${DEFAULT-VALUE}")
    private boolean includeDirs = false;

    @Option(names = {"-l", "--loop"}, description = "Controls loop functionality of workspace\nDefault: ${DEFAULT-VALUE}")
    private boolean loop = true;

    @Option(names = {"-r", "--retry"}, description = "controls if we retry or not\nDefault: ${DEFAULT-VALUE}")
    private boolean retry = true;

    @Option(names = {"--simple"}, description = "turn on simple mode\nDefault: ${DEFAULT-VALUE}")
    private boolean simple = false;

    @Option(names = {"-ft", "--fileTimestamp"},
            description = "set the use of file timestamps to control whether a file is new enough to be added to the queue\nDefault: ${DEFAULT-VALUE}")
    private boolean fileTimestamp = false;

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public void startService() {
        if (CollectionUtils.isEmpty(priorityDirectories)) {
            LOG.error("No input root or priority directories specified");
            throw new ParameterException(spec.commandLine(), "Missing required parameter '-i' for input root or priority directories");
        }

        LOG.info("Starting feeder using {} as the workspace class", workspaceClass);
        try {
            WorkSpace ws = Class.forName(workspaceClass).asSubclass(WorkSpace.class).getDeclaredConstructor(FeedCommand.class).newInstance(this);
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
            flavorSet.add(f.toUpperCase(Locale.getDefault()));
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
            this.feedOutputRoot = this.getProjectBase().resolve("DoneParsedData").toString();
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
