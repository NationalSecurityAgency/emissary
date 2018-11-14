package emissary.command;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import emissary.command.converter.PathExistsReadableConverter;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.Factory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.id.Identification;
import emissary.parser.DecomposedSession;
import emissary.parser.ParserException;
import emissary.parser.ParserFactory;
import emissary.parser.SessionParser;
import emissary.parser.SessionProducer;
import emissary.place.IServiceProviderPlace;
import emissary.util.shell.Executrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Run Identification places on a payload to determine the file type")
public class WhatCommand extends BaseCommand {

    static final Logger LOG = LoggerFactory.getLogger(WhatCommand.class);

    public static String COMMAND_NAME = "what";

    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Parameter(names = {"-h", "--header"}, description = "use header identification, defaults to true", arity = 1)
    private boolean headerIdentification = true;

    @Parameter(names = {"-i", "--input"}, description = "input file or directory", converter = PathExistsReadableConverter.class, required = true)
    private Path input;

    @Parameter(names = {"-r", "--recursive"}, description = "recurse on input directories, defaults to false")
    private boolean recursive = false;

    protected List<IServiceProviderPlace> places;
    protected List<String> placeLabels = new ArrayList<String>();
    protected Set<String> textPlaces;
    // protected DropOffUtil dropOffUtil;
    private ParserFactory parserFactory;

    @Override
    public void run(JCommander jc) {
        // TODO This entire class should be refactored into a testable unit
        setup();
        processPath(this.input);
    }

    @Override
    public void setupCommand() {
        setupWhat();
    }

    public void setupWhat() {
        setupConfig();
        // since we are not really running a server, setup a few things so the code doesn't know
        System.setProperty(EmissaryNode.NODE_NAME_PROPERTY, "localhost");
        System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "8001");
        EmissaryNode node = new EmissaryNode();
        String directoryPlaceName = "http://" + node.getNodeName() + ":" + node.getNodePort() + "/DirectoryPlace";
        try {
            Namespace.bind(directoryPlaceName, new DirectoryPlace(directoryPlaceName, node));
            parserFactory = new ParserFactory();
            final Configurator configG = ConfigUtil.getConfigInfo(this.getClass());
            final List<String> placeNames = configG.findEntries("PLACE");
            this.places = new ArrayList<IServiceProviderPlace>();
            for (final String entry : placeNames) {
                final String[] parts = entry.split("/");
                final String instanceName = parts[0];
                final String className = parts[1];
                final String configName = configG.findStringEntry("CONFIG_" + instanceName, className + ".cfg");
                try {
                    final IServiceProviderPlace place =
                            (IServiceProviderPlace) Factory.create(className, configName, "FAKE.DIR.DIR.http://localhost:8001/DirectoryPlace",
                                    "http://localhost:8001/" + instanceName);
                    this.places.add(place);
                    this.placeLabels.add(instanceName);
                } catch (Exception e) {
                    LOG.error(String.format("Could not create {} ({}): ", instanceName, className, e));
                }
            }
            LOG.debug("Created " + this.places.size() + " places");

            this.textPlaces = configG.findEntriesAsSet("TEXT_PLACE");
        } catch (IOException e) {
            LOG.error("Could not instantiate the WhatCommand: instanceName" + e.getMessage());
        }
    }

    protected void processPath(final Path inputPath) {
        if (Files.isDirectory(inputPath)) {
            if (this.recursive) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputPath)) {
                    for (Path entry : stream) {
                        LOG.debug("Handling entry {} in directory {}", entry, inputPath);
                        this.processPath(entry);
                    }
                } catch (IOException ex) {
                    LOG.error(inputPath + ": Cannot read, " + ex.getMessage());
                }
            } else {
                LOG.info(inputPath + ": DIRECTORY");
            }
        } else if (Files.isRegularFile(inputPath)) {
            try {
                processFilePath(inputPath);
            } catch (ParserException ex) {
                LOG.error(inputPath + ": Cannot parse, " + ex.getMessage());
            } catch (IOException ex) {
                LOG.error(inputPath + ": Cannot read, " + ex.getMessage());
            }
        } else {
            LOG.error(inputPath + ": NOT_A_FILE");
        }
    }

    /**
     * Process one regular file from the main routine
     *
     * @param path the string name of the file to process
     */
    protected void processFilePath(final Path path) throws ParserException, IOException {
        if (!Files.exists(path)) {
            LOG.error(path + " - NONEXISTENT");
            return;
        }

        if (!Files.isReadable(path)) {
            LOG.error(path + " - UNREADABLE");
            return;
        }

        if (Files.size(path) == 0) {
            LOG.error(path + " - EMPTY");
            return;
        }

        if (!this.headerIdentification) {
            final IBaseDataObject payload =
                    DataObjectFactory.getInstance(Executrix.readDataFromFile(path.toString()), path.toAbsolutePath().toString(),
                            Form.UNKNOWN);
            final Identification id = identify(payload, this.headerIdentification);
            LOG.info(path + " - " + payload.getFilename() + ": " + id.getTypeString());
            return;
        }

        // Using Header
        RandomAccessFile raf = null;
        try {
            final SessionParser parser = parserFactory.makeSessionParser(Files.newByteChannel(path));
            LOG.debug("Using parser " + parser.getClass().getName() + " for " + path);
            final SessionProducer producer = new SessionProducer(parser, Form.UNKNOWN);
            final String parent = path.getParent().toString();
            final String fileName = path.getFileName().toString();
            while (true) {
                try {
                    final DecomposedSession sessionHash = parser.getNextSession();
                    final IBaseDataObject payload = producer.createAndLoadDataObject(sessionHash, parent);
                    final Identification id = identify(payload, this.headerIdentification);

                    LOG.info(payload.getFilename() + "/" + fileName + ": " + id.getTypeString() + " [" + payload.dataLength() + "]");
                } catch (emissary.parser.ParserEOFException eof) {
                    LOG.debug("Parser reached end of file: ", eof);
                    // Expected EOF
                    break;
                }
            }
        } finally {
            if (raf != null) {
                raf.close();
            }
        }
    }

    protected Identification identify(final IBaseDataObject b, final boolean useHeaderArg) {
        Identification ident = null;
        if (b == null || b.data() == null) {
            ident = new Identification("BAD_SESSION");
        } else if (b != null && b.dataLength() == 0) {
            ident = new Identification(Form.EMPTY);
        } else {
            ident = runEngines(b);
        }
        if (ident.getTypeCount() == 0) {
            ident.addType(Form.UNKNOWN);
        }
        return ident;
    }

    private Identification runEngines(final IBaseDataObject b) {

        final Identification ident = new Identification();
        final List<String> typesFound = new ArrayList<String>();

        if (b != null && b.dataLength() == 0) {
            ident.addType(Form.EMPTY);
            return ident;
        }

        if (!b.currentForm().equals(Form.UNKNOWN)) {
            typesFound.add(b.currentForm());
        }

        final byte[] data = b.data();

        if (LOG.isDebugEnabled()) {
            final String ds = (data.length > 10 ? new String(data, 0, 9) : new String(data));
            LOG.debug("Running engines with data=" + ds + "...");
        }

        for (int i = 0; i < this.places.size(); i++) {
            b.setCurrentForm(Form.UNKNOWN);
            final IServiceProviderPlace place = this.places.get(i);
            final String placeLabel = this.placeLabels.get(i);

            if (unknown(ident) || (isText(ident) && this.textPlaces.contains(placeLabel))) {
                try {
                    place.process(b);
                    ident.setType(b.currentForm());
                } catch (emissary.core.ResourceException rex) {
                    // Ignore.
                    LOG.debug("Error processing object", rex);
                }
                LOG.info(placeLabel + "\tsaid " + ident);
                if (this.textPlaces.contains(placeLabel)) {
                    accumulateText(typesFound, ident);
                } else {
                    accumulate(typesFound, ident);
                }
            }
        }
        // Nb. set not add...
        ident.setTypes(typesFound);
        return ident;
    }

    /**
     * Accumulate newly found types in our list with no duplication
     */
    private void accumulate(final List<String> l, final Identification i) {
        for (final String type : i.getTypes()) {
            if (!l.contains(type)) {
                l.add(type);
            }
        }
        l.remove(Form.UNKNOWN);
    }

    private void accumulateText(final List<String> l, final Identification i) {
        for (final String s : i.getTypes()) {
            if (!l.contains(s)) {
                if ("QUOTED_PRINTABLE".equals(s)) {
                    l.add(s);
                } else {
                    l.add("/" + s + "/");
                }
            }
        }
        l.remove(Form.UNKNOWN);
    }

    private static boolean unknown(final Identification i) {
        if (i.getTypeCount() == 0) {
            return true;
        }

        if (i.getTypeCount() > 1) {
            return false;
        }

        final String t = i.getFirstType();
        return t.equals(Form.UNKNOWN);
    }

    private static boolean isText(final Identification i) {
        if (i.getTypeCount() == 0) {
            return false;
        }

        final String t = i.getFirstType();
        return "HTML".equals(t) || t.endsWith("TEXT");
    }
}
