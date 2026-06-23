package emissary.test.core.junit5;

import emissary.core.DataObjectFactory;
import emissary.core.DiffCheckConfiguration;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectDiffHelper;
import emissary.core.IBaseDataObjectXmlCodecs;
import emissary.core.IBaseDataObjectXmlHelper;
import emissary.core.channels.FileChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.kff.KffDataObjectHandler;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.LogbackTester.SimplifiedLogEvent;
import emissary.util.ByteUtil;
import emissary.util.PlaceComparisonHelper;
import emissary.util.io.ResourceReader;
import emissary.util.os.OSReleaseUtil;

import com.google.errorprone.annotations.ForOverride;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jdom2.Attribute;
import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static emissary.core.IBaseDataObjectXmlCodecs.ALT_DEFAULT_ELEMENT_ENCODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.ALWAYS_SHA256_ELEMENT_ENCODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.BASE64;
import static emissary.core.IBaseDataObjectXmlCodecs.BASE64_DECODER;
import static emissary.core.IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_DECODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.ENCODING_ATTRIBUTE_NAME;
import static emissary.core.IBaseDataObjectXmlCodecs.LENGTH_ATTRIBUTE_NAME;
import static emissary.core.IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
import static emissary.core.constants.IbdoXmlElementNames.ANSWERS;
import static emissary.core.constants.IbdoXmlElementNames.ATTACHMENT_ELEMENT_PREFIX;
import static emissary.core.constants.IbdoXmlElementNames.BAD_ALT_VIEW;
import static emissary.core.constants.IbdoXmlElementNames.BROKEN;
import static emissary.core.constants.IbdoXmlElementNames.CLASSIFICATION;
import static emissary.core.constants.IbdoXmlElementNames.CURRENT_FORM;
import static emissary.core.constants.IbdoXmlElementNames.DATA;
import static emissary.core.constants.IbdoXmlElementNames.DATA_FILE;
import static emissary.core.constants.IbdoXmlElementNames.EXTRACTED_RECORD_ELEMENT_PREFIX;
import static emissary.core.constants.IbdoXmlElementNames.EXTRACT_COUNT;
import static emissary.core.constants.IbdoXmlElementNames.FILE_TYPE;
import static emissary.core.constants.IbdoXmlElementNames.FONT_ENCODING;
import static emissary.core.constants.IbdoXmlElementNames.INDEX;
import static emissary.core.constants.IbdoXmlElementNames.INITIAL_FORM;
import static emissary.core.constants.IbdoXmlElementNames.INPUT_ALT_VIEW;
import static emissary.core.constants.IbdoXmlElementNames.NAME;
import static emissary.core.constants.IbdoXmlElementNames.NOMETA;
import static emissary.core.constants.IbdoXmlElementNames.NOVIEW;
import static emissary.core.constants.IbdoXmlElementNames.NUM_ATTACHMENTS;
import static emissary.core.constants.IbdoXmlElementNames.PARAMETER;
import static emissary.core.constants.IbdoXmlElementNames.SETUP;
import static emissary.core.constants.IbdoXmlElementNames.SHORT_NAME;
import static emissary.core.constants.IbdoXmlElementNames.VALUE;
import static emissary.core.constants.IbdoXmlElementNames.VIEW;
import static emissary.test.core.junit5.AnswerGenerator.fixDisposeRunnables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class ExtractionTest extends UnitTest {

    protected static final Logger logger = LoggerFactory.getLogger(ExtractionTest.class);

    private static final List<IBaseDataObject> NO_ATTACHMENTS = Collections.emptyList();
    private static final byte[] INCORRECT_VIEW_MESSAGE = "This is the incorrect view, the place should not have processed this view".getBytes();

    private volatile AnswerGenerator answerGenerator;

    /**
     * The list of actual logEvents generated by executing the place.
     */
    protected List<SimplifiedLogEvent> actualSimplifiedLogEvents;

    protected final KffDataObjectHandler kff = new KffDataObjectHandler(
            KffDataObjectHandler.TRUNCATE_KNOWN_DATA,
            KffDataObjectHandler.SET_FORM_WHEN_KNOWN,
            KffDataObjectHandler.SET_FILE_TYPE);

    @Nullable
    protected IServiceProviderPlace place = null;
    @Nullable
    private static final String SYSTEM_OS_RELEASE;
    @Nullable
    private static final String MAJOR_OS_VERSION;

    static {
        if (OSReleaseUtil.isUbuntu()) {
            SYSTEM_OS_RELEASE = "ubuntu";
            MAJOR_OS_VERSION = OSReleaseUtil.getMajorReleaseVersion();
        } else if (OSReleaseUtil.isCentOs()) {
            SYSTEM_OS_RELEASE = "centos";
            MAJOR_OS_VERSION = OSReleaseUtil.getMajorReleaseVersion();
        } else if (OSReleaseUtil.isRhel()) {
            SYSTEM_OS_RELEASE = "rhel";
            MAJOR_OS_VERSION = OSReleaseUtil.getMajorReleaseVersion();
        } else if (OSReleaseUtil.isMac()) {
            SYSTEM_OS_RELEASE = "mac";
            MAJOR_OS_VERSION = OSReleaseUtil.getMajorReleaseVersion();
        } else {
            SYSTEM_OS_RELEASE = null;
            MAJOR_OS_VERSION = null;
        }
    }

    /**
     * Regression mode -- This test mode compares the entire BDO instead of just what is defined in the XML. In other words,
     * the XML must define exactly the output of the Place, no more and no less. There are methods provided to generate the
     * XML required.
     *
     * @return true if regression mode, false otherwise
     */
    public boolean isStrict() {
        return false;
    }

    /**
     * Configuration of {@link IBaseDataObjectDiffHelper} needed in strict mode
     *
     * @return diff helper configuration
     */
    public DiffCheckConfiguration getDiffCheck() {
        return DiffCheckConfiguration.configure().enableData().enableKeyValueParameterDiff().build();
    }

    @Override
    public String getAnswerXsd() {
        return isStrict() ? "emissary/test/core/schemas/regression.xsd" : super.getAnswerXsd();
    }

    /**
     * Override this or set the generateAnswers system property to true to generate XML for data files.
     * <p>
     * Optionally, to generate answers file without changing code run {@code mvn clean test -DgenerateAnswers=true}
     *
     * @return defaults to false if no XML should be generated (i.e. normal case of executing tests) or true to generate
     *         automatically
     */
    protected boolean generateAnswers() {
        return isStrict() && Boolean.getBoolean("generateAnswers") && createAnswerGenerator() != null;
    }

    @Nullable
    protected AnswerGenerator createAnswerGenerator() {
        return isStrict() ? new RegressionTestAnswerGenerator() : new ExtractionTestAnswerGenerator();
    }

    protected AnswerGenerator getAnswerGenerator() {
        if (answerGenerator == null) {
            synchronized (this) {
                if (answerGenerator == null) {
                    answerGenerator = createAnswerGenerator();
                }
            }
        }
        return answerGenerator;
    }

    /**
     * This method returns the XML element decoders.
     *
     * @return the XML element decoders.
     */
    @Deprecated
    protected IBaseDataObjectXmlCodecs.ElementDecoders getDecoders() {
        return DEFAULT_ELEMENT_DECODERS;
    }

    /**
     * This method returns the XML element decoders.
     *
     * @param resource the "resource" currently be tested.
     * @return the XML element decoders.
     */
    protected IBaseDataObjectXmlCodecs.ElementDecoders getDecoders(final String resource) {
        return getDecoders();
    }

    /**
     * This method returns the XML element encoders.
     *
     * @return the XML element encoders.
     */
    @Deprecated
    protected IBaseDataObjectXmlCodecs.ElementEncoders getEncoders() {
        return isStrict() ? SHA256_ELEMENT_ENCODERS : ALT_DEFAULT_ELEMENT_ENCODERS;
    }

    /**
     * This method returns the XML element encoders.
     *
     * @param resource the "resource" currently be tested.
     * @return the XML element encoders.
     */
    protected IBaseDataObjectXmlCodecs.ElementEncoders getEncoders(final String resource) {
        return getEncoders();
    }

    @BeforeEach
    public void setUpPlace() throws Exception {
        place = createPlace();
    }

    @AfterEach
    public void tearDownPlace() {
        if (place != null) {
            place.shutDown();
            place = null;
        }
    }

    public abstract IServiceProviderPlace createPlace() throws IOException;

    public static Stream<? extends Arguments> data() {
        return getMyXmlTestParameterFiles(ExtractionTest.class);
    }

    /**
     * Allow overriding the initial form in extensions to this test.
     *
     * By default, get the initial form from the filename in the form {@code INITIAL_FORM@2.dat} where {@code INITIAL_FORM}
     * will be the initial form.
     *
     * @param resource to get the form from
     * @return the initial form
     */
    @ForOverride
    @Nullable
    protected String getInitialForm(final String resource) {
        if (!isStrict()) {
            return resource.replaceAll("^.*/([^/@]+)(@\\d+)?\\.dat$", "$1");
        }

        InitialFinalFormFormat datFile = new InitialFinalFormFormat(getResourcePath(resource));
        return datFile.getInitialForm();
    }

    /**
     * Allow the initial IBDO to be overridden - for example, adding additional previous forms
     * <p>
     * This is used in the simple case to generate an IBDO from the file on disk and override the filename
     *
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    @Nullable
    protected IBaseDataObject getInitialIbdo(final String resource) {
        Path path = getResourcePath(resource);
        SeekableByteChannelFactory sbcf = FileChannelFactory.create(path);
        String initialForm = getInitialForm(resource);

        IBaseDataObject ibdo = IBaseDataObjectXmlHelper.createStandardInitialIbdo(sbcf, isStrict() ? "Classification" : null, initialForm, kff);
        ibdo.setChannelFactory(sbcf);

        if (isStrict()) {
            InitialFinalFormFormat datFile = new InitialFinalFormFormat(path);
            ibdo.setFilename(datFile.getOriginalFileName());
        } else {
            Document controlDoc = getRequiredAnswerDocument(resource);
            ibdo.setFilename(Path.of(resource).toString());
            setupPayload(ibdo, controlDoc);
        }

        return ibdo;
    }

    /**
     * This method returns the logger name to capture log events from or null if log events are not to be checked.
     *
     * @return the logger name to capture log events from or null (the default) if log events are not to be checked.
     */
    protected String getLogbackLoggerName() {
        return null;
    }

    @ParameterizedTest(name = "{arguments}")
    @MethodSource("data")
    public void testExtractionPlace(String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        // Need a pair consisting of a .dat file and a .xml file (answers)
        Document controlDoc = getRequiredAnswerDocument(resource);

        // In XML-driven mode, resolve which .dat to use (via <dataFile> or same-name fallback).
        // In legacy DAT-driven mode the resource is already the .dat path.
        String datResource = resource.endsWith(ResourceReader.XML_SUFFIX)
                ? getDatResourceFor(resource, controlDoc)
                : resource;

        if (generateAnswers()) {
            getAnswerGenerator().generateAnswerFiles(datResource, resource, place, getInitialIbdo(datResource),
                    getEncoders(datResource), super.answerFileClassRef, getLogbackLoggerName());
        }

        try (InputStream doc = new ResourceReader().getResourceAsStream(datResource)) {
            byte[] data = IOUtils.toByteArray(doc);
            IBaseDataObject payload = DataObjectFactory.getInstance(data, datResource, getInitialForm(datResource));

            setupPayload(payload, controlDoc);
            processPreHook(payload, controlDoc);

            List<IBaseDataObject> attachments = processHeavyDutyHook(place, payload);

            processPostHook(payload, attachments);
            checkAnswersPreHook(controlDoc, payload, attachments, datResource);
            checkAnswers(controlDoc, payload, attachments, datResource);
            checkAnswersPostHook(controlDoc, payload, attachments, datResource);
        } catch (Exception ex) {
            logger.error("Error running test {}", datResource, ex);
            fail("Cannot run test " + datResource, ex);
        }
    }

    /**
     * Resolve the .dat resource path for an XML-driven test. Checks the {@code <dataFile>} element in the XML root; if
     * present, the value must be a bare filename (no path separators) and the file is resolved relative to the XML's
     * directory. If absent, falls back to the same-named .dat file in the same directory.
     *
     * @param xmlResource classpath resource path to the answer XML
     * @param answerDoc the parsed answer document
     * @return classpath resource path to the .dat file to use
     */
    protected String getDatResourceFor(final String xmlResource, final Document answerDoc) {
        Element root = answerDoc.getRootElement();
        String dataFileName = root.getChildTextTrim(DATA_FILE);
        if (StringUtils.isNotBlank(dataFileName)) {
            if (dataFileName.contains("/") || dataFileName.contains(File.separator)) {
                fail("dataFile must be a directory-local filename (no path separators): " + dataFileName);
            }
            return FilenameUtils.getPath(xmlResource) + dataFileName;
        }
        return xmlResource.substring(0, xmlResource.length() - ResourceReader.XML_SUFFIX.length())
                + ResourceReader.DATA_SUFFIX;
    }

    @Nullable
    protected Path getResourcePath(String resource) {
        try {
            return Path.of(new ResourceReader().getResource(resource).toURI());
        } catch (URISyntaxException | NullPointerException e) {
            fail("Failed to resolve resource path: " + resource, e);
            return null;
        }
    }

    protected Document getRequiredAnswerDocument(String resource) {
        Document doc = getAnswerDocumentFor(resource);
        if (doc == null) {
            fail("No answers provided for test: " + resource);
        }
        return doc;
    }

    @Override
    protected Document getAnswerDocumentFor(final String resource) {
        if (generateAnswers()) {
            return getAnswerGenerator().getAnswerDocumentFor(resource, answerFileClassRef);
        }
        // XML-driven mode: resource IS the XML file
        if (resource.endsWith(ResourceReader.XML_SUFFIX)) {
            return getAnswerDocumentValidated(resource);
        }
        // Legacy DAT-driven mode: derive XML path from the .dat resource
        return super.getAnswerDocumentFor(resource);
    }

    protected void processPreHook(IBaseDataObject payload, Document controlDoc) {
        // Nothing to do here
    }

    protected void processPostHook(IBaseDataObject payload, List<IBaseDataObject> attachments) {
        // Nothing to do here
    }

    protected List<IBaseDataObject> processHeavyDutyHook(IServiceProviderPlace place, IBaseDataObject payload) throws Exception {
        if (getLogbackLoggerName() == null) {
            actualSimplifiedLogEvents = new ArrayList<>();
            return place.agentProcessHeavyDuty(payload);
        } else {
            try (LogbackTester logbackTester = new LogbackTester(getLogbackLoggerName())) {
                final List<IBaseDataObject> attachments = place.agentProcessHeavyDuty(payload);
                actualSimplifiedLogEvents = logbackTester.getSimplifiedLogEvents();
                return attachments;
            }
        }
    }

    /**
     * When the data is able to be retrieved from the XML (e.g. when getEncoders() returns the default encoders), then this
     * method should be empty. However, in this case getEncoders() is returning the sha256 encoders which means the original
     * data cannot be retrieved from the XML. Therefore, in order to test equivalence, all the non-printable data in the
     * IBaseDataObjects needs to be converted to a sha256 hash. The full encoders can be used by overriding the
     * checkAnswersPreHook(...) to be empty and overriding getEncoders() to return the DEFAULT_ELEMENT_ENCODERS.
     */
    protected void checkAnswersPreHook(Document answers, IBaseDataObject payload, List<IBaseDataObject> attachments, String tname) {

        if (getLogbackLoggerName() != null) {
            checkAnswersPreHookLogEvents(actualSimplifiedLogEvents);
        }

        IBaseDataObjectXmlCodecs.ElementEncoders encoders = getEncoders(tname);
        boolean alwaysHash = ALWAYS_SHA256_ELEMENT_ENCODERS.equals(encoders);

        // Only proceed if we are using a hashing encoder
        if (!alwaysHash && !SHA256_ELEMENT_ENCODERS.equals(encoders)) {
            return;
        }

        // Hash alternate views
        payload.getAlternateViews().forEach((viewName, bytes) -> hashIfNecessary(bytes, alwaysHash)
                .ifPresent(hash -> payload.addAlternateView(viewName, hash.getBytes(StandardCharsets.UTF_8))));

        // Hash primary data
        hashIfNecessary(payload.data(), alwaysHash).ifPresent(hash -> payload.setData(hash.getBytes(StandardCharsets.UTF_8)));

        // Hash extracted records
        Optional.ofNullable(payload.getExtractedRecords()).ifPresent(records -> records.forEach(rec -> hashIfNecessary(rec.data(), alwaysHash)
                .ifPresent(hash -> rec.setData(hash.getBytes(StandardCharsets.UTF_8)))));

        // Hash attachments
        Optional.ofNullable(attachments).ifPresent(atts -> atts.stream()
                .filter(att -> ByteUtil.hasNonPrintableValues(att.data()))
                .forEach(att -> hashIfNecessary(att.data(), alwaysHash)
                        .ifPresent(hash -> att.setData(hash.getBytes(StandardCharsets.UTF_8)))));

        fixDisposeRunnables(payload);
    }

    protected void checkAnswersPreHook(Element answers, IBaseDataObject payload, IBaseDataObject attachment, String tname) {
        // Nothing to do here
    }

    protected Optional<String> hashIfNecessary(byte[] data, boolean alwaysHash) {
        return hashBytesIfNonPrintable(data, alwaysHash);
    }

    /**
     * Generates a SHA 256 hash of the provided bytes if they contain any non-printable characters
     *
     * @param bytes the bytes to evaluate
     * @param alwaysHash overrides the non-printable check and always hashes the bytes.
     * @return a value optionally containing the generated hash
     */
    protected Optional<String> hashBytesIfNonPrintable(byte[] bytes, boolean alwaysHash) {
        if (ArrayUtils.isEmpty(bytes)) {
            return Optional.empty();
        }

        if (alwaysHash || ByteUtil.containsNonIndexableBytes(bytes)) {
            return Optional.ofNullable(ByteUtil.sha256Bytes(bytes));
        }

        return Optional.empty();
    }

    /**
     * This method allows log events to be modified prior to checkAnswers being called.
     *
     * In the default case, do nothing.
     *
     * @param simplifiedLogEvents the log events to be tweaked.
     */
    protected void checkAnswersPreHookLogEvents(List<SimplifiedLogEvent> simplifiedLogEvents) {
        // No-op unless overridden
    }

    protected void checkAnswersPostHook(Document answers, IBaseDataObject payload, List<IBaseDataObject> attachments, String tname) {
        // Nothing to do here
    }

    protected void checkAnswersPostHook(Element answers, IBaseDataObject payload, IBaseDataObject attachment, String tname) {
        // Nothing to do here
    }

    protected void checkAnswers(Document answers, IBaseDataObject payload, List<IBaseDataObject> attachments, String tname)
            throws DataConversionException {
        Element root = answers.getRootElement();
        Element parent = root.getChild(ANSWERS);

        if (isStrict()) {
            assertNotNull(parent, "No 'answers' section found!");
            checkAnswersStrict(answers, payload, attachments, tname, parent);
        } else {
            checkAnswersLenient(Optional.ofNullable(parent).orElse(root), payload, attachments, tname);
        }
    }

    protected void checkAnswers(Element el, IBaseDataObject payload, @Nullable List<IBaseDataObject> attachments, String tname)
            throws DataConversionException {
        checkAttachmentCounts(el, attachments, tname);
        checkCurrentForms(el, payload, tname);
        checkFields(el, payload, tname);
        checkMetadata(el, payload, tname);
        checkViews(el, payload, tname);
        checkExtractedRecords(el, payload, tname);
    }

    /**
     * LENIENT: Recursive element-based check for payload and attachments.
     *
     * @param parent XML element for the parent
     * @param payload The base data object
     * @param attachments The list of child base data objects
     * @param tname the path of the dat file
     * @throws DataConversionException data conversion from a string to value type fails
     */
    protected void checkAnswersLenient(Element parent, IBaseDataObject payload,
            List<IBaseDataObject> attachments, String tname) throws DataConversionException {
        // Check the main payload
        checkAnswers(parent, payload, attachments, tname);

        // Check each attachment individually
        for (int attNum = 1; attNum <= attachments.size(); attNum++) {
            String atname = tname + Family.SEP + attNum;
            Element el = getChildAnswers(parent, ATTACHMENT_ELEMENT_PREFIX, attNum);

            if (el != null) {
                IBaseDataObject currentAtt = attachments.get(attNum - 1);

                checkAnswersPreHook(el, payload, currentAtt, atname);
                checkAnswers(el, currentAtt, null, atname);
                checkAnswersPostHook(el, payload, currentAtt, atname);
            }
        }
    }

    /**
     * STRICT: Regression comparison using PlaceComparisonHelper.
     *
     * @param answers XML document containing the expected results
     * @param payload The base data object
     * @param attachments The list of child base data objects
     * @param tname the path of the dat file
     * @param parent XML element for the parent
     */
    protected void checkAnswersStrict(Document answers, IBaseDataObject payload,
            List<IBaseDataObject> attachments, String tname, Element parent) {
        final List<IBaseDataObject> expectedAttachments = new ArrayList<>();
        final IBaseDataObject expectedIbdo = IBaseDataObjectXmlHelper.ibdoFromXml(answers, expectedAttachments, getDecoders(tname));

        assertNotNull(place);
        final String differences = PlaceComparisonHelper.checkDifferences(
                expectedIbdo, payload,
                expectedAttachments, attachments,
                place.getClass().getName(), getDiffCheck());

        String message = generateAnswers()
                ? differences + "\nNOTE: Since 'generateAnswers' is true, these differences could indicate non-deterministic processing\n"
                : differences;

        assertNull(differences, message);

        // Strict mode also validates Log Events
        assertIterableEquals(SimplifiedLogEvent.fromXml(parent), actualSimplifiedLogEvents);
    }

    protected void checkAttachmentCounts(Element el, List<IBaseDataObject> attachments, String tname) {
        int payloadSize = attachments != null ? attachments.size() : 0;

        Element numAttEl = null;
        long numAttElements = 0;

        for (Element child : el.getChildren()) {
            if (verifyOs(child)) {
                String name = child.getName();
                if (name.equals(NUM_ATTACHMENTS) && numAttEl == null) {
                    numAttEl = child;
                } else if (name.startsWith(ATTACHMENT_ELEMENT_PREFIX)) {
                    numAttElements++;
                }
            }
        }

        if (numAttEl != null) {
            int numAtt = Integer.parseInt(numAttEl.getValue());
            assertEquals(numAtt, payloadSize,
                    String.format(Locale.getDefault(), "Expected <numAttachments> in %s not equal to number of att in payload.", tname));
        } else if (numAttElements > 0) {
            assertEquals(numAttElements, payloadSize,
                    String.format(Locale.getDefault(), "Expected <att#> in %s not equal to number of att in payload.", tname));
        } else if (payloadSize > 0) {
            fail(String.format(Locale.getDefault(),
                    "%d attachments in payload with no count in answer xml, add matching <numAttachments> count for %s",
                    payloadSize, tname));
        }
    }

    protected void checkCurrentForms(Element el, IBaseDataObject payload, String tname) throws DataConversionException {
        for (Element currentForm : el.getChildren(CURRENT_FORM)) {
            if (verifyOs(currentForm)) {
                String cf = currentForm.getTextTrim();
                if (cf != null) {
                    Attribute index = currentForm.getAttribute(INDEX);
                    if (index != null) {
                        assertEquals(payload.currentFormAt(index.getIntValue()), cf,
                                String.format(Locale.getDefault(), "Current form '%s' not found at position [%d] in %s, %s",
                                        cf, index.getIntValue(), tname, payload.getAllCurrentForms()));
                    } else {
                        assertTrue(payload.searchCurrentForm(cf) > -1,
                                String.format(Locale.getDefault(), "Current form '%s' not found in %s, %s", cf, tname, payload.getAllCurrentForms()));
                    }
                }
            }
        }
    }

    protected void checkFields(Element el, IBaseDataObject payload, String tname) {
        // File Type
        assertStringProperty(el, FILE_TYPE, payload.getFileType(),
                ft -> String.format(Locale.getDefault(), "Expected File Type '%s' in %s", ft, tname));

        // Current Form Size
        assertIntProperty(el, "currentFormSize", payload.currentFormSize(),
                size -> String.format(Locale.getDefault(), "Current form size '%d' does not match in %s", size, tname));

        // Classification
        assertStringProperty(el, CLASSIFICATION, payload.getClassification(),
                cl -> String.format(Locale.getDefault(), "Classification in '%s' is '%s', not expected '%s'", tname, payload.getClassification(),
                        cl));

        // Data Length
        assertIntProperty(el, "dataLength", payload.dataLength(),
                len -> String.format(Locale.getDefault(), "Data length '%d' does not match in %s", len, tname));

        // Short Name
        assertStringProperty(el, SHORT_NAME, payload.shortName(), val -> "Shortname does not match expected in " + tname);

        // Font Encoding
        assertStringProperty(el, FONT_ENCODING, payload.getFontEncoding(), val -> "Font encoding does not match expected in " + tname);

        // Broken
        assertStringProperty(el, BROKEN, Boolean.toString(payload.isBroken()), val -> "Broken status in " + tname);

        // Processing Error
        assertProcessingError(el, payload.getProcessingError(), expected -> String.format("Expected processing error '%s' in %s", expected, tname),
                () -> "Processing Error does not match expected in " + tname);
    }

    protected void checkMetadata(Element el, IBaseDataObject payload, String tname) {
        // Parameters
        for (Element meta : el.getChildren(PARAMETER)) {
            if (verifyOs(meta)) {
                String key = meta.getChildTextTrim(NAME);
                checkForMissingNameElement(PARAMETER, key, tname);
                checkStringValue(meta, payload.getStringParameter(key), tname);
            }
        }

        // Nometa
        for (Element meta : el.getChildren(NOMETA)) {
            if (verifyOs(meta)) {
                String key = meta.getChildTextTrim(NAME);
                checkForMissingNameElement(NOMETA, key, tname);
                assertFalse(payload.hasParameter(key),
                        String.format(Locale.getDefault(), "Metadata element '%s' in '%s' should not exist, but has value of '%s'", key, tname,
                                payload.getStringParameter(key)));
            }
        }
    }

    protected void checkViews(Element el, IBaseDataObject payload, String tname) {
        // Primary Data View
        List<Element> dataElements = el.getChildren(DATA);
        if (!dataElements.isEmpty()) {
            String primaryDataStr = new String(payload.data()); // Allocated once safely out of loop
            for (Element dataEl : dataElements) {
                if (verifyOs(dataEl)) {
                    int length = NumberUtils.toInt(dataEl.getChildTextTrim(LENGTH_ATTRIBUTE_NAME), -1);
                    if (length > -1) {
                        assertEquals(length, payload.dataLength(), "Data length in " + tname);
                    }
                    checkStringValue(dataEl, primaryDataStr, tname);
                }
            }
        }

        // Alternate Views
        for (Element view : el.getChildren(VIEW)) {
            if (verifyOs(view)) {
                String viewName = view.getChildTextTrim(NAME);
                byte[] viewData = payload.getAlternateView(viewName);
                assertNotNull(viewData, String.format(Locale.getDefault(), "Alternate View '%s' is missing in %s", viewName, tname));

                String lengthStr = view.getChildTextTrim(LENGTH_ATTRIBUTE_NAME);
                if (lengthStr != null) {
                    assertEquals(Integer.parseInt(lengthStr), viewData.length,
                            String.format(Locale.getDefault(), "Length of Alternate View '%s' is wrong in %s", viewName, tname));
                }
                checkStringValue(view, new String(viewData), tname);
            }
        }

        // Noview
        for (Element view : el.getChildren(NOVIEW)) {
            if (verifyOs(view)) {
                String viewName = view.getChildTextTrim(NAME);
                assertNull(payload.getAlternateView(viewName),
                        String.format(Locale.getDefault(), "Alternate View '%s' is present, but should not be, in %s", viewName, tname));
            }
        }
    }

    protected void checkExtractedRecords(Element el, IBaseDataObject payload, String tname) throws DataConversionException {
        List<IBaseDataObject> extractedChildren = payload.hasExtractedRecords() ? payload.getExtractedRecords() : List.of();
        int payloadSize = extractedChildren.size();

        Element extractCountEl = null;
        long numExtractElements = 0;

        for (Element child : el.getChildren()) {
            if (verifyOs(child)) {
                String name = child.getName();
                if (name.equals(EXTRACT_COUNT) && extractCountEl == null) {
                    extractCountEl = child;
                } else if (name.startsWith(EXTRACTED_RECORD_ELEMENT_PREFIX)) {
                    numExtractElements++;
                }
            }
        }

        int extractCount = extractCountEl != null ? Integer.parseInt(extractCountEl.getValue()) : -1;
        if (extractCount > -1) {
            assertEquals(extractCount, payloadSize,
                    String.format(Locale.getDefault(), "Expected <extractCount> in %s not equal to number of extracts in payload.", tname));
        } else if (numExtractElements > 0) {
            assertEquals(numExtractElements, payloadSize,
                    String.format(Locale.getDefault(), "Expected <extract#> in %s not equal to number of extracts in payload.", tname));
        } else if (payloadSize > 0) {
            fail(String.format(Locale.getDefault(),
                    "%d extracts in payload with no count in answer xml, add matching <extractCount> count for %s", payloadSize, tname));
        }

        for (int attNum = 1; attNum <= payloadSize; attNum++) {
            Element extel = getChildAnswers(el, EXTRACTED_RECORD_ELEMENT_PREFIX, attNum);
            if (extel != null) {
                checkAnswers(extel, extractedChildren.get(attNum - 1), NO_ATTACHMENTS,
                        String.format(Locale.getDefault(), "%s::extract%d", tname, attNum));
            }
        }
    }

    protected void assertStringProperty(Element parent, String childName, String actualValue, Function<String, String> messageProvider) {
        for (Element child : parent.getChildren(childName)) {
            if (verifyOs(child)) {
                String expectedValue = child.getTextTrim();
                if (StringUtils.isNotBlank(expectedValue)) {
                    assertEquals(expectedValue, actualValue, messageProvider.apply(expectedValue));
                }
            }
        }
    }

    protected void assertIntProperty(Element parent, String childName, int actualValue, Function<Integer, String> messageProvider) {
        for (Element child : parent.getChildren(childName)) {
            if (verifyOs(child)) {
                int expectedValue;
                try {
                    expectedValue = Integer.parseInt(child.getValue());
                } catch (NumberFormatException e) {
                    expectedValue = -1;
                }
                if (expectedValue > -1) {
                    assertEquals(expectedValue, actualValue, messageProvider.apply(expectedValue));
                }
            }
        }
    }

    protected void assertProcessingError(Element parent, String actualError, Function<String, String> notNullMessageProvider,
            Supplier<String> equalityMessageProvider) {
        for (Element child : parent.getChildren("procError")) {
            if (verifyOs(child)) {
                String expectedError = child.getTextTrim();
                if (StringUtils.isNotBlank(expectedError)) {
                    assertNotNull(actualError, notNullMessageProvider.apply(expectedError));
                    String normalizedActual = actualError.replace("\n", ";");
                    assertEquals(expectedError, normalizedActual, equalityMessageProvider.get());
                }
            }
        }
    }

    private static void checkForMissingNameElement(String parentTag, String key, String tname) {
        if (key == null) {
            fail(String.format(Locale.getDefault(), "The element %s has a problem in %s: does not have a child name element", parentTag, tname));
        }
    }

    protected void checkStringValue(Element meta, String data, String tname) {
        Element valueElement = meta.getChild(VALUE);
        String value = (valueElement != null) ? valueElement.getText() : null;
        if (value == null || "null".equalsIgnoreCase(value)) {
            return;
        }

        // Determine matchMode: Priority to 'encoding' attribute, then 'matchMode', default to 'equals'
        String encoding = valueElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);
        String matchMode = (encoding != null && !encoding.isEmpty())
                ? encoding
                : meta.getAttributeValue("matchMode", "equals");

        String key = meta.getChildTextTrim(NAME);

        var truncatedData = truncate(data);
        var truncatedValue = truncate(value);

        switch (matchMode) {
            case "equals":
                assertEquals(value, data, formatErr(meta, key, tname, "does not equal", truncatedData, truncatedValue));
                break;

            case INDEX:
            case "contains":
                assertTrue(data.contains(value), formatErr(meta, key, tname, "does not contain", truncatedData, truncatedValue));
                break;

            case "!index":
            case "!contains":
                assertFalse(data.contains(value), formatErr(meta, key, tname, "should not contain", truncatedData, truncatedValue));
                break;

            case "match":
                assertTrue(data.matches(value), formatErr(meta, key, tname, "does not match regex", truncatedData, truncatedValue));
                break;

            case BASE64:
                // decode value as a base64 encoded byte[] array and use the string
                // representation of the byte array for comparison to the incoming value
                value = new String(BASE64_DECODER.decode(value));
                truncatedValue = truncate(value);
                assertEquals(value, data, formatErr(meta, key, tname, "Base64 mismatch", truncatedData, truncatedValue));
                break;

            default:
                if ("collection".equalsIgnoreCase(matchMode)) {
                    handleCollectionMatch(meta, data, value, key, tname);
                } else {
                    fail(String.format("Problematic matchMode '%s' for test '%s' in %s", matchMode, key, meta.getName()));
                }
                break;
        }
    }

    protected void handleCollectionMatch(Element meta, String data, String value, String key, String tname) {
        Attribute sepAttr = meta.getAttribute("collectionSeparator");
        String separator = (sepAttr != null) ? sepAttr.getValue() : ",";

        List<String> expectedValues = Arrays.asList(value.split(separator));
        List<String> actualValues = Arrays.asList(data.split(separator));

        String msg = String.format("%s element '%s' in %s: collections not equal. Sep: '%s'",
                meta.getName(), key, tname, separator);
        assertTrue(CollectionUtils.isEqualCollection(expectedValues, actualValues), msg);
    }

    protected String formatErr(Element meta, String key, String tname, String reason, String actual, String expected) {
        return String.format(Locale.getDefault(), "%s element '%s' problem in %s: '%s' %s '%s'",
                meta.getName(), key, tname, actual, reason, expected);
    }

    protected String truncate(String input) {
        return truncate(input, 150);
    }

    protected String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }

    protected boolean verifyOs(Element element) {
        Attribute specifiedOs = element.getAttribute("os-release");
        Attribute specifiedVersion = element.getAttribute("os-version");
        if (specifiedOs != null) {
            String os = specifiedOs.getValue();
            switch (os) {
                case "ubuntu":
                case "centos":
                case "rhel":
                case "mac":
                    if (specifiedVersion != null) {
                        return os.equals(SYSTEM_OS_RELEASE) && specifiedVersion.getValue().equals(MAJOR_OS_VERSION);
                    } else {
                        // verify os matches, major os version not specified
                        return os.equals(SYSTEM_OS_RELEASE);
                    }
                default:
                    fail("specified OS needs to match ubuntu, centos, rhel, or mac. Provided OS=" + os);
            }
        }
        // os-release is not set as an attribute, element applicable for all os
        return true;
    }

    protected void setupPayload(IBaseDataObject payload, Document doc) {
        if (!isStrict()) {
            kff.hash(payload);
        }

        Element root = (doc != null) ? doc.getRootElement() : null;
        if (root == null) {
            return;
        }

        Element setup = root.getChild(SETUP);
        boolean didSetFiletype = false;
        if (setup != null) {
            if (isStrict()) {
                payload.popCurrentForm();
                payload.setFileType(null);
                didSetFiletype = true;
            } else if (!setup.getChildren(INITIAL_FORM).isEmpty()) {
                payload.popCurrentForm();
            }

            IBaseDataObjectXmlHelper.ibdoFromXmlMainElements(setup, payload, getDecoders(payload.getFilename()));

            String fileType = setup.getChildTextTrim(FILE_TYPE);
            if (StringUtils.isNotBlank(fileType)) {
                payload.setFileType(fileType);
                didSetFiletype = true;
            }

            String inputAlternateView = setup.getChildTextTrim(INPUT_ALT_VIEW);
            if (StringUtils.isNotBlank(inputAlternateView)) {
                payload.addAlternateView(inputAlternateView, payload.data());
                payload.setData(INCORRECT_VIEW_MESSAGE);
            }

            final String badAlternateView = setup.getChildTextTrim(BAD_ALT_VIEW);
            if (StringUtils.isNotBlank(badAlternateView)) {
                payload.addAlternateView(badAlternateView, INCORRECT_VIEW_MESSAGE);
            }
        }

        if (!didSetFiletype) {
            payload.setFileType(payload.currentForm());
        }
    }

    protected Element getChildAnswers(Element parent, String name, int index) {
        // look up the new way i.e <att index="1">
        Element el = parent.getChildren().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name) && c.getAttribute(INDEX).getValue().equals(String.valueOf(index)) && verifyOs(c))
                .findFirst()
                .orElse(null);
        if (el == null) {
            // fallback to the old way i.e. <att1>
            el = parent.getChildren().stream().filter(c -> c.getName().equalsIgnoreCase(name + index) && verifyOs(c))
                    .findFirst()
                    .orElse(null);
        }
        return el;
    }
}
