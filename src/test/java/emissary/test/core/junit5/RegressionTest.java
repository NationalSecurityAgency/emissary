package emissary.test.core.junit5;

import emissary.core.BaseDataObject;
import emissary.core.DiffCheckConfiguration;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlCodecs.ElementDecoders;
import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.core.IBaseDataObjectXmlHelper;
import emissary.core.channels.FileChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.LogbackTester.SimplifiedLogEvent;
import emissary.util.ByteUtil;
import emissary.util.DisposeHelper;
import emissary.util.PlaceComparisonHelper;
import emissary.util.io.ResourceReader;

import com.google.errorprone.annotations.ForOverride;
import jakarta.annotation.Nullable;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.lang3.ArrayUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static emissary.core.IBaseDataObjectXmlCodecs.ALWAYS_SHA256_ELEMENT_ENCODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_DECODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
import static emissary.core.constants.IbdoXmlElementNames.ANSWERS;
import static emissary.core.constants.IbdoXmlElementNames.SETUP;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <p>
 * This test acts similarly to ExtractionTest; however, it compares the entire BDO instead of just what is defined in
 * the XML. In other words, the XML must define exactly the output of the Place, no more and no less. There are methods
 * provided to generate the XML required.
 * </p>
 * 
 * <p>
 * To implement this for a test, you should:
 * </p>
 * <ol>
 * <li>Extend this class</li>
 * <li>Either Override {@link #generateAnswers()} to return true or set the generateAnswers system property to true,
 * which will generate the XML answer files</li>
 * <li>Optionally, to generate answers file without changing code run {@code mvn clean test -DgenerateAnswers=true}</li>
 * <li>Optionally, override the various provided methods if you want to customise the behaviour of providing the IBDO
 * before/after processing</li>
 * <li>Run the tests, which should pass - if they don't, you either have incorrect processing which needs fixing, or you
 * need to further customise the initial/final IBDOs.</li>
 * <li>Once the tests pass, you can remove the overridden method(s) added above.</li>
 * </ol>
 */
public abstract class RegressionTest extends ExtractionTest {

    /* XML builder to read XML answer file in */
    private static final SAXBuilder XML_BUILDER = new SAXBuilder(XMLReaders.NONVALIDATING);

    /* Open options for (over-)writing answers XML */
    private static final Set<StandardOpenOption> CREATE_WRITE_TRUNCATE = new HashSet<>(Arrays.asList(StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

    /* Difference configuration to use when comparing IBDO's. */
    private static final DiffCheckConfiguration DIFF_CHECK = DiffCheckConfiguration.configure().enableData().enableKeyValueParameterDiff().build();

    /* test/resources folder */
    private static final Path TEST_RESX;

    static {
        // Gets us the parent folder to PROJECT_BASE
        Path pathBuilder = Paths.get(System.getenv("PROJECT_BASE"));
        // If in Docker, we need to go into src - we're probably already in it otherwise
        if (pathBuilder.endsWith("main")) { // Docker
            pathBuilder = pathBuilder.getParent();
        } else if (pathBuilder.getFileName().toString().contains("target")) {
            pathBuilder = pathBuilder.getParent().resolve("src");
        }
        // Append test/resources to finish the path off
        TEST_RESX = pathBuilder.resolve("test/resources");
    }


    @Override
    public String getAnswerXsd() {
        return "emissary/test/core/schemas/regression.xsd";
    }

    /**
     * Override this or set the generateAnswers system property to true to generate XML for data files.
     *
     * @return defaults to false if no XML should be generated (i.e. normal case of executing tests) or true to generate
     *         automatically
     */
    @ForOverride
    protected boolean generateAnswers() {
        return Boolean.getBoolean("generateAnswers");
    }

    /**
     * Allow the initial IBDO to be overridden - for example, adding additional previous forms
     *
     * This is used in the simple case to generate an IBDO from the file on disk and override the filename
     *
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    @ForOverride
    @Nullable
    protected IBaseDataObject getInitialIbdo(final String resource) {
        IBaseDataObject ibdo = new ClearDataBaseDataObject();
        try {
            final Path datFileUrl = Paths.get(new ResourceReader().getResource(resource).toURI());
            final InitialFinalFormFormat datFile = new InitialFinalFormFormat(datFileUrl);
            final SeekableByteChannelFactory sbcf = FileChannelFactory.create(datFile.getPath());
            // Create a BDO for the data, and set the filename correctly
            final IBaseDataObject initialIbdo = IBaseDataObjectXmlHelper.createStandardInitialIbdo(ibdo, sbcf, "Classification",
                    datFile.getInitialForm(), kff);
            initialIbdo.setChannelFactory(sbcf);
            initialIbdo.setFilename(datFile.getOriginalFileName());

            return initialIbdo;
        } catch (final URISyntaxException e) {
            fail("Couldn't get path for resource: " + resource, e);
            return null;
        }
    }

    /**
     * Allow the initial IBDO to be overridden before serializing to XML.
     * 
     * In the default case, we null out the data in the BDO which will force the data to be loaded from the .dat file
     * instead.
     * 
     * @param resource path to the dat file
     * @param initialIbdo to tweak
     */
    @ForOverride
    protected void tweakInitialIbdoBeforeSerialization(final String resource, final IBaseDataObject initialIbdo) {
        if (initialIbdo instanceof ClearDataBaseDataObject) {
            ((ClearDataBaseDataObject) initialIbdo).clearData();
        } else {
            fail("Didn't get an expected type of IBaseDataObject");
        }
    }

    /**
     * Allow the generated IBDO to be overridden - for example, adding certain field values. Will modify the provided IBDO.
     * 
     * This is used in the simple case to set the current form for the final object to be taken from the file name. If the
     * test worked correctly no change will be made, but if there is a discrepancy this will be highlighted afterwards when
     * the diff takes place.
     * 
     * @param resource path to the dat file
     * @param finalIbdo the existing final BDO after it's been processed by a place
     */
    @ForOverride
    protected void tweakFinalIbdoBeforeSerialization(final String resource, final IBaseDataObject finalIbdo) {
        try {
            final Path datFileUrl = Paths.get(new ResourceReader().getResource(resource).toURI());
            final InitialFinalFormFormat datFile = new InitialFinalFormFormat(datFileUrl);
            if (!finalIbdo.currentForm().equals(datFile.getFinalForm())) {
                final String format = "Final form from place [%s] didn't match final form in filename [%s]";
                fail(String.format(format, finalIbdo.currentForm(), datFile.getFinalForm()));
            }
        } catch (final URISyntaxException e) {
            fail("Couldn't get path for resource: " + resource, e);
        }

        fixDisposeRunnables(finalIbdo);
    }

    /**
     * Allow the children generated by the place to be overridden before serializing to XML.
     * 
     * In the default case, do nothing.
     * 
     * @param resource path to the dat file
     * @param children to tweak
     */
    @ForOverride
    protected void tweakFinalResultsBeforeSerialization(final String resource, final List<IBaseDataObject> children) {
        // No-op unless overridden
    }

    /**
     * Allows the log events generated by the place to be modified before serializing to XML.
     * 
     * In the default case, do nothing.
     * 
     * @param resource path to the dat file
     * @param simplifiedLogEvents to tweak
     */
    @ForOverride
    protected void tweakFinalLogEventsBeforeSerialization(final String resource, final List<SimplifiedLogEvent> simplifiedLogEvents) {
        // No-op unless overridden
    }

    @Override
    @ForOverride
    @Nullable
    protected String getInitialForm(final String resource) {
        try {
            final Path datFileUrl = Paths.get(new ResourceReader().getResource(resource).toURI());
            final InitialFinalFormFormat datFile = new InitialFinalFormFormat(datFileUrl);
            return datFile.getInitialForm();
        } catch (final URISyntaxException e) {
            fail("Unable to get initial form from filename", e);
            return null;
        }
    }

    /**
     * This method returns the XML element decoders.
     * 
     * @return the XML element decoders.
     */
    @Deprecated
    protected ElementDecoders getDecoders() {
        return DEFAULT_ELEMENT_DECODERS;
    }

    /**
     * This method returns the XML element decoders.
     * 
     * @param resource the "resource" currently be tested.
     * @return the XML element decoders.
     */
    protected ElementDecoders getDecoders(final String resource) {
        return getDecoders();
    }

    /**
     * This method returns the XML element encoders.
     * 
     * @return the XML element encoders.
     */
    @Deprecated
    protected ElementEncoders getEncoders() {
        return SHA256_ELEMENT_ENCODERS;
    }

    /**
     * This method returns the XML element encoders.
     * 
     * @param resource the "resource" currently be tested.
     * @return the XML element encoders.
     */
    protected ElementEncoders getEncoders(final String resource) {
        return getEncoders();
    }

    /**
     * When the data is able to be retrieved from the XML (e.g. when getEncoders() returns the default encoders), then this
     * method should be empty. However, in this case getEncoders() is returning the sha256 encoders which means the original
     * data cannot be retrieved from the XML. Therefore, in order to test equivalence, all of the non-printable data in the
     * IBaseDataObjects needs to be converted to a sha256 hash. The full encoders can be used by overriding the
     * checkAnswersPreHook(...) to be empty and overriding getEncoders() to return the DEFAULT_ELEMENT_ENCODERS.
     */
    @Override
    protected void checkAnswersPreHook(final Document answers, final IBaseDataObject payload, final List<IBaseDataObject> attachments,
            final String tname) {

        if (getLogbackLoggerName() != null) {
            checkAnswersPreHookLogEvents(actualSimplifiedLogEvents);
        }

        final boolean alwaysHash;

        if (SHA256_ELEMENT_ENCODERS.equals(getEncoders(tname))) {
            alwaysHash = false;
        } else if (ALWAYS_SHA256_ELEMENT_ENCODERS.equals(getEncoders(tname))) {
            alwaysHash = true;
        } else {
            return;
        }

        // touch up alternate views to match how their bytes would have encoded into the answer file
        for (Entry<String, byte[]> entry : new TreeMap<>(payload.getAlternateViews()).entrySet()) {
            Optional<String> viewSha256 = hashBytesIfNonPrintable(entry.getValue(), alwaysHash);
            viewSha256.ifPresent(s -> payload.addAlternateView(entry.getKey(), s.getBytes(StandardCharsets.UTF_8)));
        }

        // touch up primary view if necessary
        Optional<String> payloadSha256 = hashBytesIfNonPrintable(payload.data(), alwaysHash);
        payloadSha256.ifPresent(s -> payload.setData(s.getBytes(StandardCharsets.UTF_8)));

        if (payload.getExtractedRecords() != null) {
            for (final IBaseDataObject extractedRecord : payload.getExtractedRecords()) {
                Optional<String> recordSha256 = hashBytesIfNonPrintable(extractedRecord.data(), alwaysHash);
                recordSha256.ifPresent(s -> extractedRecord.setData(s.getBytes(StandardCharsets.UTF_8)));
            }
        }

        if (attachments != null) {
            for (final IBaseDataObject attachment : attachments) {
                if (ByteUtil.hasNonPrintableValues(attachment.data())) {
                    Optional<String> attachmentSha256 = hashBytesIfNonPrintable(attachment.data(), alwaysHash);
                    attachmentSha256.ifPresent(s -> attachment.setData(s.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }

        fixDisposeRunnables(payload);
    }

    /**
     * Generates a SHA 256 hash of the provided bytes if they contain any non-printable characters
     * 
     * @param bytes the bytes to evaluate
     * @param alwaysHash overrides the non-printable check and always hashes the bytes.
     * @return a value optionally containing the generated hash
     */
    protected Optional<String> hashBytesIfNonPrintable(byte[] bytes, final boolean alwaysHash) {
        if (ArrayUtils.isNotEmpty(bytes) && (alwaysHash || ByteUtil.containsNonIndexableBytes(bytes))) {
            return Optional.ofNullable(ByteUtil.sha256Bytes(bytes));
        }

        return Optional.empty();
    }

    protected static class ClearDataBaseDataObject extends BaseDataObject {
        private static final long serialVersionUID = -8728006876784881020L;

        protected void clearData() {
            theData = null;
            seekableByteChannelFactory = null;
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    @Override
    public void testExtractionPlace(final String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        if (generateAnswers()) {
            try {
                generateAnswerFiles(resource);
            } catch (final Exception e) {
                logger.error("Error running test {}", resource, e);
                fail("Unable to generate answer file", e);
            }
        }

        // Run the normal extraction/regression tests
        super.testExtractionPlace(resource);
    }

    /**
     * Actually generate the answer file for a given resource
     * 
     * Takes initial form and final forms from the filename
     * 
     * @param resource to generate against
     * @throws Exception if an error occurs during processing
     */
    protected void generateAnswerFiles(final String resource) throws Exception {
        // Get the data and create a channel factory to it
        final IBaseDataObject initialIbdo = getInitialIbdo(resource);
        // Clone the BDO to create an 'after' copy
        final IBaseDataObject finalIbdo = IBaseDataObjectHelper.clone(initialIbdo);
        // Actually process the BDO and keep the children
        final List<IBaseDataObject> finalResults;
        final List<SimplifiedLogEvent> finalLogEvents;
        if (getLogbackLoggerName() == null) {
            finalResults = place.agentProcessHeavyDuty(finalIbdo);
            finalLogEvents = new ArrayList<>();
        } else {
            try (LogbackTester logbackTester = new LogbackTester(getLogbackLoggerName())) {
                finalResults = place.agentProcessHeavyDuty(finalIbdo);
                finalLogEvents = logbackTester.getSimplifiedLogEvents();
            }
        }

        // Allow overriding things before serializing to XML
        tweakInitialIbdoBeforeSerialization(resource, initialIbdo);
        tweakFinalIbdoBeforeSerialization(resource, finalIbdo);
        tweakFinalResultsBeforeSerialization(resource, finalResults);
        tweakFinalLogEventsBeforeSerialization(resource, finalLogEvents);

        // Generate the full XML (setup & answers from before & after)
        writeAnswerXml(resource, initialIbdo, finalIbdo, finalResults, finalLogEvents, getEncoders(resource),
                super.answerFileClassRef);
    }

    @Override
    protected List<IBaseDataObject> processHeavyDutyHook(IServiceProviderPlace place, IBaseDataObject payload)
            throws Exception {
        if (getLogbackLoggerName() == null) {
            actualSimplifiedLogEvents = new ArrayList<>();
            return super.processHeavyDutyHook(place, payload);
        } else {
            try (LogbackTester logbackTester = new LogbackTester(getLogbackLoggerName())) {
                final List<IBaseDataObject> attachments = super.processHeavyDutyHook(place, payload);

                actualSimplifiedLogEvents = logbackTester.getSimplifiedLogEvents();

                return attachments;
            }
        }
    }

    @Override
    @Nullable
    protected Document getAnswerDocumentFor(final String resource) {
        // If generating answers, get the src version, otherwise get the normal XML file
        if (generateAnswers()) {
            try {
                final Path path = getXmlPath(resource, answerFileClassRef);
                return path == null ? null : XML_BUILDER.build(path.toFile());
            } catch (final JDOMException | IOException e) {
                // Fail if invalid XML document
                fail(String.format("No valid answer document provided for %s", resource), e);
                return null;
            }
        }

        return super.getAnswerDocumentFor(resource);
    }

    @Override
    protected void setupPayload(final IBaseDataObject payload, final Document answers) {
        final Element root = answers.getRootElement();

        if (root != null) {
            final Element parent = root.getChild(SETUP);

            if (parent != null) {
                payload.popCurrentForm(); // Remove default form put on by ExtractionTest.
                payload.setFileType(null); // Remove default filetype put on by ExtractionTest.
                // The only other fields set are data and filename.

                IBaseDataObjectXmlHelper.ibdoFromXmlMainElements(parent, payload, getDecoders(payload.getFilename()));
            }
        }
    }

    @Override
    protected void checkAnswers(final Document answers, final IBaseDataObject payload,
            final List<IBaseDataObject> attachments, final String tname) {

        final Element root = answers.getRootElement();
        final Element parent = root.getChild(ANSWERS);

        assertNotNull(parent, "No 'answers' section found!");

        final List<IBaseDataObject> expectedAttachments = new ArrayList<>();
        final IBaseDataObject expectedIbdo = IBaseDataObjectXmlHelper.ibdoFromXml(answers, expectedAttachments, getDecoders(tname));
        final String differences = PlaceComparisonHelper.checkDifferences(expectedIbdo, payload, expectedAttachments,
                attachments, place.getClass().getName(), DIFF_CHECK);

        assertNull(differences, generateAnswers() ? differences
                + "\nNOTE: Since 'generateAnswers' is true, these differences could indicate non-deterministic processing in the tested code path\n"
                : differences);

        assertIterableEquals(SimplifiedLogEvent.fromXml(parent), actualSimplifiedLogEvents);
    }

    /**
     * Default behavior to fix dispose runnables to change "variant" to "invariant"
     *
     * @param ibdo the base data object containing dispose runnables
     */
    protected void fixDisposeRunnables(final IBaseDataObject ibdo) {
        if (ibdo.hasParameter(DisposeHelper.KEY)) {
            final List<Object> values = ibdo.getParameter(DisposeHelper.KEY);
            final List<String> newValues = new ArrayList<>();

            for (Object o : values) {
                newValues.add(o.getClass().getName());
            }

            ibdo.putParameter(DisposeHelper.KEY, newValues);
        }
    }

    /**
     * Generate the relevant XML and write to disk.
     *
     * @param resource referencing the DAT file
     * @param initialIbdo for 'setup' section
     * @param finalIbdo for 'answers' section
     * @param encoders for encoding ibdo into XML
     * @param results for 'answers' section
     * @param answerFileClassRef answer file class (if different from data class)
     */
    protected static void writeAnswerXml(final String resource, final IBaseDataObject initialIbdo, final IBaseDataObject finalIbdo,
            final List<IBaseDataObject> results, final List<SimplifiedLogEvent> logEvents, final ElementEncoders encoders,
            @Nullable final AtomicReference<Class<?>> answerFileClassRef) {
        final Element rootElement = IBaseDataObjectXmlHelper.xmlElementFromIbdo(finalIbdo, results, initialIbdo, encoders);
        final Element answerElement = rootElement.getChild(ANSWERS);

        SimplifiedLogEvent.toXml(logEvents).forEach(answerElement::addContent);

        // Generate the full XML (setup & answers from before & after)
        final byte[] xmlContent = bytesFromDocument(new Document(rootElement));
        // Write out the XML to disk
        writeXml(resource, xmlContent, answerFileClassRef);
    }

    @Nullable
    private static byte[] bytesFromDocument(final Document jdom) {
        final Format format = Format.getPrettyFormat().setLineSeparator(LineSeparator.UNIX);
        final XMLOutputter outputter = new XMLOutputter(format);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            outputter.output(jdom, os);
            return os.toByteArray();
        } catch (IOException iox) {
            return null;
        }
    }

    /**
     * Helper method to write XML for a given DAT file.
     *
     * @param resource referencing the DAT file
     * @param xmlContent to write to the XML answer file
     * @param answerFileClassRef answer file class (if different from data class)
     */
    protected static void writeXml(final String resource, final byte[] xmlContent, @Nullable final AtomicReference<Class<?>> answerFileClassRef) {
        final Path path = getXmlPath(resource, answerFileClassRef);
        if (path == null) {
            fail(String.format("Could not get path for resource = %s", resource));
        }
        logger.info("Writing answers file to path: {}", path);
        try (FileChannel fc = FileChannel.open(path, CREATE_WRITE_TRUNCATE);
                SeekableInMemoryByteChannel simbc = new SeekableInMemoryByteChannel(xmlContent)) {
            fc.transferFrom(simbc, 0, simbc.size());
        } catch (final IOException ioe) {
            fail(String.format("Couldn't write XML answer file for resource: %s", resource), ioe);
        }
    }

    /**
     * Gets the XML filename/path for the given resource (a .dat file)
     *
     * @param resource path to the .dat file
     * @param answerFileClassRef answer file class (if different from data class)
     * @return path to the corresponding .xml file
     */
    @Nullable
    protected static Path getXmlPath(final String resource, @Nullable final AtomicReference<Class<?>> answerFileClassRef) {
        final int datPos = resource.lastIndexOf(ResourceReader.DATA_SUFFIX);
        if (datPos == -1) {
            logger.debug("Resource is not a DATA file {}", resource);
            return null;
        }

        String xmlPath;
        if (answerFileClassRef == null) {
            xmlPath = resource.substring(0, datPos) + ResourceReader.XML_SUFFIX;
        } else {
            String ansPath = answerFileClassRef.get().getName().replace(".", "/");
            int testNamePos = resource.lastIndexOf("/");
            xmlPath = ansPath + resource.substring(testNamePos, datPos) + ResourceReader.XML_SUFFIX;
        }
        return TEST_RESX.resolve(xmlPath);
    }
}
