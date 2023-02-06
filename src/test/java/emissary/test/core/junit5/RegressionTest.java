package emissary.test.core.junit5;

import emissary.core.BaseDataObject;
import emissary.core.DiffCheckConfiguration;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.channels.FileChannelFactory;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.core.channels.SeekableByteChannelHelper;
import emissary.kff.KffDataObjectHandler;
import emissary.util.PlaceComparisonHelper;
import emissary.util.io.ResourceReader;
import emissary.util.xml.AbstractJDOMUtil;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.lang3.Validate;
import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;

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
 * <li>Override {@link #generateAnswers()} to return true, which will generate the XML answer files</li>
 * <li>Optionally, override {@link #getInitialIbdo(String)} and {@link #modifyFinalIbdo(String, IBaseDataObject)} if you
 * want to customise the behaviour of providing the initial IBDO, such as overriding the current form before/after
 * processing</li>
 * <li>Run the tests, which should pass - if they don't, you either have incorrect processing which needs fixing, or you
 * need to further customise the initial/final IBDOs.</li>
 * <li>Once the tests pass, you can remove the overridden method(s) added above.</li>
 * </ol>
 */
public abstract class RegressionTest extends ExtractionTest {
    // Everything below here is generic enough to be used by other test classes
    // Extending tests should override generateAnswers - and data & createPlace like usual.

    // Test resource directory
    private static Path TEST_RESX;
    // XML builder to read XML answer file in
    private static final SAXBuilder xmlBuilder = new SAXBuilder(XMLReaders.NONVALIDATING);
    // Open options for (over-)writing answers XML
    private static final Set<StandardOpenOption> CREATE_WRITE_TRUNCATE = new HashSet<>(Arrays.asList(StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
    // Default configuration to only check data when comparing
    private static final DiffCheckConfiguration DIFF_CHECK = DiffCheckConfiguration.onlyCheckData();

    /**
     * Dynamically finds the core/src/test/resources directory to write the XML to.
     * 
     * Running locally in an IDE, PROJECT_BASE will likely point to core/src/main/ Running in Maven/Docker it will most
     * likely point to core/target/
     */
    @BeforeAll
    protected static void setupPaths() {
        // Gets us the parent folder to PROJECT_BASE
        Path pathBuilder = Paths.get(System.getenv("PROJECT_BASE")).getParent();
        // If in Docker, we need to go into src - we're probably already in it otherwise
        if (pathBuilder.endsWith("core")) { // Docker
            pathBuilder = pathBuilder.resolve("src");
        }
        // Append test/resources to finish the path off
        TEST_RESX = pathBuilder.resolve("test/resources");
    }

    /**
     * Override this to generate XML for data files
     * 
     * Return the initial form for the provided dat files. If multiple types are to be tested,
     * 
     * @return defaults to false if no XML should be generated (i.e. normal case of executing tests) or true to generate
     *         automatically
     */
    protected boolean generateAnswers() {
        return false;
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
                fail("Unable to generate answer files", e);
            }
        }

        // Run the normal extraction/regression tests
        super.testExtractionPlace(resource);
    }

    /**
     * Allow the initial IBDO to be overridden - for example, adding additional previous forms
     * 
     * This is used in the simple case to generate an IBDO from the file on disk and override the filename
     * 
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    protected IBaseDataObject getInitialIbdo(final String resource) {
        return getInitialIbdoWithFormInFilename(resource);
    }

    /**
     * Simple/default way to provide the initial IBDO
     * 
     * Takes the data from the dat file and sets the current (initial) form based on the filename
     * 
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    protected final IBaseDataObject getInitialIbdoWithFormInFilename(final String resource) {
        try {
            final Path datFileUrl = Paths.get(new ResourceReader().getResource(resource).toURI());
            final TestFileFormat datFile = new TestFileFormat(datFileUrl);
            final SeekableByteChannelFactory sbcf = FileChannelFactory.create(datFile.getPath());
            // Create a BDO for the data, and set the filename correctly
            final IBaseDataObject initialIbdo = createStandardInitialIbdo(sbcf, "Classification", datFile.getInitialForm(), kff);
            initialIbdo.setChannelFactory(sbcf);
            initialIbdo.setFilename(datFile.getOriginalFileName());

            return initialIbdo;
        } catch (final URISyntaxException e) {
            fail("Couldn't get path for resource: " + resource, e);
            return null;
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
    protected void modifyFinalIbdo(final String resource, final IBaseDataObject finalIbdo) {
        modifyFinalIbdoWithFormInFilename(resource, finalIbdo);
    }

    /**
     * Simple/default way to provide the final IBDO. Will modify the provided IBDO.
     * 
     * Sets the current (final) form based on the filename
     * 
     * @param resource path to the dat file
     * @param finalIbdo the existing final BDO after it's been processed by a place
     */
    protected final void modifyFinalIbdoWithFormInFilename(final String resource, final IBaseDataObject finalIbdo) {
        try {
            final Path datFileUrl = Paths.get(new ResourceReader().getResource(resource).toURI());
            final TestFileFormat datFile = new TestFileFormat(datFileUrl);
            finalIbdo.setCurrentForm(datFile.getFinalForm());
        } catch (final URISyntaxException e) {
            fail("Couldn't get path for resource: " + resource, e);
        }
    }

    /**
     * Actually generate the answer file for a given resource
     * 
     * Takes initial form & final forms from the filename
     * 
     * @param resource to generate against
     * @throws Exception if an error occurs during processing
     */
    private void generateAnswerFiles(final String resource) throws Exception {
        // Get the data and create a channel factory to it
        final IBaseDataObject initialIbdo = getInitialIbdo(resource);

        // Clone the BDO to create an 'after' copy
        final IBaseDataObject finalIbdo = IBaseDataObjectHelper.clone(initialIbdo, true);
        // Actually process the BDO and keep the children
        final List<IBaseDataObject> results = place.agentProcessHeavyDuty(finalIbdo);

        // Allow overriding final IBDO before serialising to XML
        modifyFinalIbdo(resource, finalIbdo);

        // Generate the full XML (setup & answers from before & after)
        final String xmlContent = xmlFromIbdo(finalIbdo, results, initialIbdo);
        // Write out the XML to disk
        writeXml(resource, xmlContent);
    }

    /**
     * When generating XML answer files, we need to use the src version rather than target
     * 
     * This method returns the XML file from that location.
     * 
     * @return the XML file in the src directory (not target)
     */
    @Override
    protected Document getAnswerDocumentFor(final String resource) {
        // Short circuit to getting the answers document the regular way
        if (!generateAnswers()) {
            return super.getAnswerDocumentFor(resource);
        }

        try {
            return xmlBuilder.build(getXmlPath(resource).toFile());
        } catch (final JDOMException | IOException e) {
            logger.debug(String.format("No valid answer document provided for %s", resource), e);
            return null;
        }
    }

    /**
     * Gets the XML filename/path for the given resource (a .dat file)
     * 
     * @param resource path to the .dat file
     * @return path to the corresponding .xml file
     */
    private static Path getXmlPath(final String resource) {
        final int datPos = resource.lastIndexOf(ResourceReader.DATA_SUFFIX);
        if (datPos == -1) {
            logger.debug("Resource is not a DATA file {}", resource);
            return null;
        }

        final String xmlPath = resource.substring(0, datPos) + ResourceReader.XML_SUFFIX;
        return TEST_RESX.resolve(xmlPath);
    }

    /**
     * Helper method to write XML for a given DAT file.
     * 
     * @param resource referencing the DAT file
     * @param xmlContent to write to the XML answer file
     */
    private static void writeXml(final String resource, final String xmlContent) {
        final Path path = getXmlPath(resource);
        logger.info("Writing answers file to path: {}", path.toString());
        try (FileChannel fc = FileChannel.open(path, CREATE_WRITE_TRUNCATE);
                SeekableInMemoryByteChannel simbc = new SeekableInMemoryByteChannel(xmlContent.getBytes())) {
            fc.transferFrom(simbc, 0, simbc.size());
        } catch (final IOException ioe) {
            fail(String.format("Couldn't write XML answer file for resource: %s", resource), ioe);
        }
    }

    // Similar but not same as regex in PayloadUtil
    private static final Pattern validForm = Pattern.compile("([\\w-)(/]+)");

    /**
     * Utility class to represent a dat file name.
     * 
     * Format should be INITIAL_FORM.FINAL_FORM.OPTIONAL_COMMENT.dat
     */
    private class TestFileFormat {
        private String initialForm;
        private String finalForm;
        private String comments;
        private final String baseFileName;
        private final String originalFileName;
        private final Path path;

        public TestFileFormat(final Path path) {
            this.path = path;
            originalFileName = path.getFileName().toString();
            baseFileName = originalFileName.substring(0, originalFileName.lastIndexOf("."));
            initialForm = null;
            finalForm = null;
            comments = null;
            fillComponents();
        }

        private void fillComponents() {
            final Matcher fileNameMatcher = validForm.matcher(baseFileName);

            if (fileNameMatcher.find()) {
                initialForm = fileNameMatcher.group();
                if (fileNameMatcher.find()) {
                    finalForm = fileNameMatcher.group();
                    if (fileNameMatcher.find()) {
                        comments = fileNameMatcher.group();
                    }
                }
            }
        }

        /**
         * Return the initial portion of a filename, if it exists (null if not)
         *
         * @return null if no initial form exists, or the initial form
         */
        private String getInitialForm() {
            return initialForm;
        }

        /**
         * Return the final form portion of a filename, if it exists (null if not)
         * 
         * @return null if no final form exists, or the final form
         */
        private String getFinalForm() {
            return finalForm;
        }

        /**
         * Returns the comments portion of a filename, if it exists (null if not)
         * 
         * @return null if no comment exists, or the comment
         */
        private String getComments() {
            return comments;
        }

        /**
         * Get base name without extension. e.g. INITIAL_FORM.FINAL_FORM.OPTIONAL_COMMENT.dat becomes
         * INITIAL_FORM.FINAL_FORM.OPTIONAL_COMMENT
         * 
         * @return the base filename
         */
        private String getBaseFileName() {
            return baseFileName;
        }

        /**
         * Returns the original filename
         * 
         * @return the original filename
         */
        private String getOriginalFileName() {
            return originalFileName;
        }

        /**
         * Returns the original full path to the file
         * 
         * @return the original full path to the file
         */
        private Path getPath() {
            return path;
        }
    }

    @Override
    protected void setupPayload(final IBaseDataObject payload, final Document answers) {
        final Element root = answers.getRootElement();

        if (root != null) {
            final Element parent = root.getChild(SETUP_ELEMENT_NAME);

            if (parent != null) {
                payload.popCurrentForm(); // Remove default form put on by ExtractionTest.
                payload.setFileType(null); // Remove default filetype put on by ExtractionTest.
                // The only other fields set are data and filename.

                ibdoFromXmlMainElements(parent, payload);
            }
        }
    }

    @Override
    protected void checkAnswers(final Document answers, final IBaseDataObject payload,
            final List<IBaseDataObject> attachments, final String tname) throws DataConversionException {
        final Element root = answers.getRootElement();
        final Element parent = root.getChild(ANSWERS_ELEMENT_NAME);

        assertNotNull(parent, "No 'answers' section found!");

        final List<IBaseDataObject> expectedAttachments = new ArrayList<>();
        final IBaseDataObject expectedIbdo = ibdoFromXml(answers, expectedAttachments);
        final String differences = PlaceComparisonHelper.checkDifferences(expectedIbdo, payload, expectedAttachments,
                attachments, place.getClass().getName(), DIFF_CHECK);

        assertNull(differences, differences);
    }

    /**
     * The XML element name for Answers.
     */
    public static final String ANSWERS_ELEMENT_NAME = "answers";
    /**
     * The XML element prefix for attachments.
     */
    public static final String ATTACHMENT_ELEMENT_PREFIX = "att";
    /**
     * The XML attribute name for Base64.
     */
    public static final String BASE64_ATTRIBUTE_NAME = "base64";
    /**
     * New line byte array to use for normalised XML
     */
    private static final byte[] BASE64_NEW_LINE_BYTE = new byte[] {'\n'};
    /**
     * New line string to use for normalised XML
     */
    private static final String BASE64_NEW_LINE_STRING = new String(BASE64_NEW_LINE_BYTE);
    /**
     * Max width of Base64 char block.
     */
    private static final int BASE64_LINE_WIDTH = 76;
    /**
     * The Base64 decoder.
     */
    private static final Base64.Decoder BASE64_DECODER = Base64.getMimeDecoder();
    /**
     * The Base64 encoder.
     * 
     * Uses same width as default, but overrides new line separator to use normalised XML separator.
     * 
     * @see http://www.jdom.org/docs/apidocs/org/jdom2/output/Format.html#setLineSeparator(java.lang.String)
     */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getMimeEncoder(BASE64_LINE_WIDTH, BASE64_NEW_LINE_BYTE);
    /**
     * The XML element name for Birth Order.
     */
    public static final String BIRTH_ORDER_ELEMENT_NAME = "birthOrder";
    /**
     * The IBaseDataObject set method name for Birth Order.
     */
    public static final String BIRTH_ORDER_SET_METHOD_NAME = "setBirthOrder";
    /**
     * The XML element name for Broken.
     */
    public static final String BROKEN_ELEMENT_NAME = "broken";
    /**
     * The IBaseDataObject set method name for Broken.
     */
    public static final String BROKEN_SET_METHOD_NAME = "setBroken";
    /**
     * The XML element name for Classification.
     */
    public static final String CLASSIFICATION_ELEMENT_NAME = "classification";
    /**
     * The IBaseDataObject set method name for Classification.
     */
    public static final String CLASSIFICATION_SET_METHOD_NAME = "setClassification";
    /**
     * The XML element name for Current Form.
     */
    public static final String CURRENT_FORM_ELEMENT_NAME = "currentForm";
    /**
     * The IBaseDataObject set method name for Current Form.
     */
    public static final String CURRENT_FORM_SET_METHOD_NAME = "pushCurrentForm";
    /**
     * The XML element name for Data.
     */
    public static final String DATA_ELEMENT_NAME = "data";
    /**
     * The IBaseDataObject set method name for Data.
     */
    public static final String DATA_SET_METHOD_NAME = "setChannelFactory";
    /**
     * The XML attribute name for Encoding.
     */
    public static final String ENCODING_ATTRIBUTE_NAME = "encoding";
    /**
     * The XML element prefix for Extracted Records.
     */
    public static final String EXTRACTED_RECORD_ELEMENT_PREFIX = "extract";
    /**
     * The XML element name for Filename.
     */
    public static final String FILENAME_ELEMENT_NAME = "filename";
    /**
     * The IBaseDataObject set method name for Filename.
     */
    public static final String FILENAME_SET_METHOD_NAME = "setFilename";
    /**
     * The XML element name for Font Encoding.
     */
    public static final String FONT_ENCODING_ELEMENT_NAME = "fontEncoding";
    /**
     * The IBaseDataObject set method name for Font Encoding.
     */
    public static final String FONT_ENCODING_SET_METHOD_NAME = "setFontEncoding";
    /**
     * The XML element name for Footer.
     */
    public static final String FOOTER_ELEMENT_NAME = "footer";
    /**
     * The IBaseDataObject set method name for Footer.
     */
    public static final String FOOTER_SET_METHOD_NAME = "setFooter";
    /**
     * The XML element name for Header.
     */
    public static final String HEADER_ELEMENT_NAME = "header";
    /**
     * The IBaseDataObject set method name for Header.
     */
    public static final String HEADER_SET_METHOD_NAME = "setHeader";
    /**
     * The XML element name for Header Encoding.
     */
    public static final String HEADER_ENCODING_ELEMENT_NAME = "headerEncoding";
    /**
     * The IBaseDataObject set method name for Header Encoding.
     */
    public static final String HEADER_ENCODING_SET_METHOD_NAME = "setHeaderEncoding";
    /**
     * The XML element name for Id.
     */
    public static final String ID_ELEMENT_NAME = "id";
    /**
     * The IBaseDataObject set method name for Id.
     */
    public static final String ID_SET_METHOD_NAME = "setId";
    /**
     * The XML element name for Name.
     */
    public static final String NAME_ELEMENT_NAME = "name";
    /**
     * The XML element name for Num Siblings.
     */
    public static final String NUM_CHILDREN_ELEMENT_NAME = "numChildren";
    /**
     * The IBaseDataObject set method name for Num Siblings.
     */
    public static final String NUM_CHILDREN_SET_METHOD_NAME = "setNumChildren";
    /**
     * The XML element name for Num Siblings.
     */
    public static final String NUM_SIBLINGS_ELEMENT_NAME = "numSiblings";
    /**
     * The IBaseDataObject set method name for Num Siblings.
     */
    public static final String NUM_SIBLINGS_SET_METHOD_NAME = "setNumSiblings";
    /**
     * The XML element name for Outputable.
     */
    public static final String OUTPUTABLE_ELEMENT_NAME = "outputable";
    /**
     * The IBaseDataObject set method name for Outputable.
     */
    public static final String OUTPUTABLE_SET_METHOD_NAME = "setOutputable";
    /**
     * The XML element name for Parameters.
     */
    public static final String PARAMETER_ELEMENT_NAME = "meta";
    /**
     * The IBaseDataObject set method name for Parameters.
     */
    public static final String PARAMETER_SET_METHOD_NAME = "putParameter";
    /**
     * The XML element name for Priority.
     */
    public static final String PRIORITY_ELEMENT_NAME = "priority";
    /**
     * The IBaseDataObject set method name for Priority.
     */
    public static final String PRIORITY_SET_METHOD_NAME = "setPriority";
    /**
     * The XML element name for Processing Error.
     */
    public static final String PROCESSING_ERROR_ELEMENT_NAME = "processingError";
    /**
     * The IBaseDataObject set method name for Processing Error.
     */
    public static final String PROCESSING_ERROR_SET_METHOD_NAME = "addProcessingError";
    /**
     * The XML element name for Result.
     */
    public static final String RESULT_ELEMENT_NAME = "result";
    /**
     * The XML element name for Transaction Id.
     */
    public static final String TRANSACTION_ID_ELEMENT_NAME = "transactionId";
    /**
     * The IBaseDataObject set method name for Transaction Id.
     */
    public static final String TRANSACTION_ID_SET_METHOD_NAME = "setTransactionId";
    /**
     * The XML element name for Value.
     */
    public static final String VALUE_ELEMENT_NAME = "value";
    /**
     * The XML element name for View.
     */
    public static final String VIEW_ELEMENT_NAME = "view";
    /**
     * The IBaseDataObject set method name for View.
     */
    public static final String VIEW_SET_METHOD_NAME = "addAlternateView";
    /**
     * The XML element name for Work Bundle Id.
     */
    public static final String WORK_BUNDLE_ID_ELEMENT_NAME = "workBundleId";
    /**
     * The IBaseDataObject set method name for Work Bundle Id.
     */
    public static final String WORK_BUNDLE_ID_SET_METHOD_NAME = "setWorkBundleId";
    /**
     * The XML element name for Setup.
     */
    public static final String SETUP_ELEMENT_NAME = "setup";
    /**
     * The XML namespace for "xml".
     */
    public static final Namespace XML_NAMESPACE = Namespace.getNamespace(XMLConstants.XML_NS_PREFIX,
            XMLConstants.XML_NS_URI);
    /**
     * A map of element names of IBaseDataObject methods that get/set primitives and their default values.
     */
    public static final Map<String, Object> PRIMITVE_NAME_DEFAULT_MAP = Collections
            .unmodifiableMap(new ConcurrentHashMap<>(Stream.of(
                    new SimpleEntry<>(BIRTH_ORDER_ELEMENT_NAME, new BaseDataObject().getBirthOrder()),
                    new SimpleEntry<>(BROKEN_ELEMENT_NAME, new BaseDataObject().isBroken()),
                    new SimpleEntry<>(NUM_CHILDREN_ELEMENT_NAME, new BaseDataObject().getNumChildren()),
                    new SimpleEntry<>(NUM_SIBLINGS_ELEMENT_NAME, new BaseDataObject().getNumSiblings()),
                    new SimpleEntry<>(OUTPUTABLE_ELEMENT_NAME, new BaseDataObject().isOutputable()),
                    new SimpleEntry<>(PRIORITY_ELEMENT_NAME, new BaseDataObject().getPriority()))
                    .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue))));

    /**
     * Local logging instance.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RegressionTest.class);

    /**
     * Interface for decoding an element value.
     */
    private interface ElementDecoder {
        /**
         * Decodes an XML element.
         * 
         * @param element to decode.
         * @return the decoded element value.
         */
        Object decode(Element element);

        /**
         * Returns the class of the key for a mapped value or null for a non-mapped value.
         * 
         * @return the class of the key for a mapped value or null for a non-mapped value.
         */
        Class<?> getKeyClass();

        /**
         * Returns the class of the value, whether mapped or non-mapped.
         * 
         * @return the class of the value, whether mapped or non-mapped.
         */
        Class<?> getValueClass();
    }

    /**
     * Implementation of an XML element decoder that has a boolean value.
     */
    private static ElementDecoder booleanDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            return Boolean.valueOf(element.getValue());
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return boolean.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a SeekableByteChannel value.
     */
    private static ElementDecoder seekableByteChannelFactoryDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return InMemoryChannelFactory.create(extractBytes(encoding, elementValue));
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return SeekableByteChannelFactory.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a byte array value.
     */
    private static ElementDecoder byteArrayDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return extractBytes(encoding, elementValue);
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return byte[].class;
        }
    };

    /**
     * Implementation of an XML element decoder that has an integer value.
     */
    private static ElementDecoder integerDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            try {
                return Integer.decode(element.getValue());
            } catch (final NumberFormatException e) {
                return null;
            }
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return int.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a string value.
     */
    private static ElementDecoder stringDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final String elementValue = element.getValue();
            final String encoding = element.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return new String(extractBytes(encoding, elementValue), StandardCharsets.UTF_8);
        }

        @Override
        public Class<?> getKeyClass() {
            return null;
        }

        @Override
        public Class<?> getValueClass() {
            return String.class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a mapped value where the key is a string and the value is a byte
     * array.
     */
    private static ElementDecoder stringByteArrayDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final Element childElement = element.getChild(VALUE_ELEMENT_NAME);
            final String elementValue = childElement.getValue();
            final String encoding = childElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return extractBytes(encoding, elementValue);
        }

        @Override
        public Class<?> getKeyClass() {
            return String.class;
        }

        @Override
        public Class<?> getValueClass() {
            return byte[].class;
        }
    };

    /**
     * Implementation of an XML element decoder that has a mapped value where the key is a string and the value is an
     * object.
     */
    private static ElementDecoder stringObjectDecoder = new ElementDecoder() {
        @Override
        public Object decode(final Element element) {
            final Element childElement = element.getChild(VALUE_ELEMENT_NAME);
            final String elementValue = childElement.getValue();
            final String encoding = childElement.getAttributeValue(ENCODING_ATTRIBUTE_NAME);

            return new String(extractBytes(encoding, elementValue), StandardCharsets.UTF_8);
        }

        @Override
        public Class<?> getKeyClass() {
            return String.class;
        }

        @Override
        public Class<?> getValueClass() {
            return Object.class;
        }
    };

    /**
     * Return UTF8 bytes from an XML value, decoding base64 if required
     * 
     * @param encoding e.g. 'base64', otherwise it returns the bytes as they are presented
     * @param elementValue containing the data
     * @return the data from elementValue, decoded from base64 if required
     */
    private static byte[] extractBytes(final String encoding, final String elementValue) {
        if (BASE64_ATTRIBUTE_NAME.equalsIgnoreCase(encoding)) {
            final String newElementValue = elementValue.replace("\n", "");
            final byte[] bytes = newElementValue.getBytes(StandardCharsets.UTF_8);
            return BASE64_DECODER.decode(bytes);
        } else {
            return elementValue.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Setup a typical BDO
     * 
     * @param sbcf initial channel factory for the data
     * @param classification initial classification string
     * @param formAndFileType initial form and file type
     * @param kff an existing Kff handler
     * @return a typical BDO with the specified data
     */
    public static IBaseDataObject createStandardInitialIbdo(final SeekableByteChannelFactory sbcf,
            final String classification, final String formAndFileType, final KffDataObjectHandler kff) {
        final IBaseDataObject ibdo = new BaseDataObject();
        final IBaseDataObject tempIbdo = new BaseDataObject();

        tempIbdo.setChannelFactory(sbcf);
        kff.hash(tempIbdo);
        ibdo.setParameters(tempIbdo.getParameters());

        ibdo.setCurrentForm(formAndFileType);
        ibdo.setFileType(formAndFileType);
        ibdo.setClassification(classification);

        return ibdo;
    }

    /**
     * Creates an IBaseDataObject and associated children from an XML document.
     * 
     * @param document containing the IBaseDataObject and children descriptions.
     * @param children the list where the children will be added.
     * @return the IBaseDataObject.
     */
    public static IBaseDataObject ibdoFromXml(final Document document, final List<IBaseDataObject> children) {
        Validate.notNull(document, "Required document != null!");
        Validate.notNull(children, "Required children != null!");

        final Element root = document.getRootElement();
        final Element answersElement = root.getChild(ANSWERS_ELEMENT_NAME);
        final IBaseDataObject parentIbdo = new BaseDataObject();
        final List<Element> answerChildren = answersElement.getChildren();

        ibdoFromXmlMainElements(answersElement, parentIbdo);

        for (final Element answerChild : answerChildren) {
            final IBaseDataObject childIbdo = new BaseDataObject();
            final String childName = answerChild.getName();

            if (childName.startsWith(EXTRACTED_RECORD_ELEMENT_PREFIX)) {
                parentIbdo.addExtractedRecord(ibdoFromXmlMainElements(answerChild, childIbdo));
            } else if (childName.startsWith(ATTACHMENT_ELEMENT_PREFIX)) {
                children.add(ibdoFromXmlMainElements(answerChild, childIbdo));
            }
        }

        return parentIbdo;
    }

    /**
     * Creates an IBaseDataObject from an XML element excluding Extracted Records and children.
     * 
     * @param element to create IBaseDataObject from.
     * @param ibdo to apply the element values to.
     * @return the IBaseDataObject that was passed in.
     */
    public static IBaseDataObject ibdoFromXmlMainElements(final Element element, final IBaseDataObject ibdo) {
        parseElement(element.getChild(DATA_ELEMENT_NAME), ibdo, DATA_SET_METHOD_NAME,
                seekableByteChannelFactoryDecoder);
        parseElement(element.getChild(BIRTH_ORDER_ELEMENT_NAME), ibdo, BIRTH_ORDER_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(BROKEN_ELEMENT_NAME), ibdo, BROKEN_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(CLASSIFICATION_ELEMENT_NAME), ibdo, CLASSIFICATION_SET_METHOD_NAME,
                stringDecoder);

        for (final Element currentForm : element.getChildren(CURRENT_FORM_ELEMENT_NAME)) {
            parseElement(currentForm, ibdo, CURRENT_FORM_SET_METHOD_NAME, stringDecoder);
        }

        parseElement(element.getChild(FILENAME_ELEMENT_NAME), ibdo, FILENAME_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(FONT_ENCODING_ELEMENT_NAME), ibdo, FONT_ENCODING_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(FOOTER_ELEMENT_NAME), ibdo, FOOTER_SET_METHOD_NAME, byteArrayDecoder);
        parseElement(element.getChild(HEADER_ELEMENT_NAME), ibdo, HEADER_SET_METHOD_NAME, byteArrayDecoder);
        parseElement(element.getChild(HEADER_ENCODING_ELEMENT_NAME), ibdo, HEADER_ENCODING_SET_METHOD_NAME,
                stringDecoder);
        parseElement(element.getChild(ID_ELEMENT_NAME), ibdo, ID_SET_METHOD_NAME, stringDecoder);
        parseElement(element.getChild(NUM_CHILDREN_ELEMENT_NAME), ibdo, NUM_CHILDREN_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(NUM_SIBLINGS_ELEMENT_NAME), ibdo, NUM_SIBLINGS_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(OUTPUTABLE_ELEMENT_NAME), ibdo, OUTPUTABLE_SET_METHOD_NAME, booleanDecoder);
        parseElement(element.getChild(PRIORITY_ELEMENT_NAME), ibdo, PRIORITY_SET_METHOD_NAME, integerDecoder);
        parseElement(element.getChild(PROCESSING_ERROR_ELEMENT_NAME), ibdo, PROCESSING_ERROR_SET_METHOD_NAME,
                stringDecoder);
        parseElement(element.getChild(TRANSACTION_ID_ELEMENT_NAME), ibdo, TRANSACTION_ID_SET_METHOD_NAME,
                stringDecoder);
        parseElement(element.getChild(WORK_BUNDLE_ID_ELEMENT_NAME), ibdo, WORK_BUNDLE_ID_SET_METHOD_NAME,
                stringDecoder);

        for (final Element parameter : element.getChildren(PARAMETER_ELEMENT_NAME)) {
            parseElement(parameter, ibdo, PARAMETER_SET_METHOD_NAME, stringObjectDecoder);
        }

        for (final Element view : element.getChildren(VIEW_ELEMENT_NAME)) {
            parseElement(view, ibdo, VIEW_SET_METHOD_NAME, stringByteArrayDecoder);
        }

        return ibdo;
    }

    /**
     * Parse an element to set the value on a BDO
     * 
     * @param element to get the data from
     * @param ibdo to set the data on
     * @param ibdoMethodName to use to set the data
     * @param elementDecoder to use to decode the element data
     */
    private static void parseElement(final Element element, final IBaseDataObject ibdo, final String ibdoMethodName,
            final ElementDecoder elementDecoder) {
        if (element != null) {
            final Object parameter = elementDecoder.decode(element);

            if (parameter != null) {
                setParameterOnIbdo(elementDecoder.getKeyClass(), elementDecoder.getValueClass(), ibdo, ibdoMethodName,
                        parameter, element);
            }
        }
    }

    /**
     * Set a parameter on a specific BDO
     * 
     * @param keyClass to use for the key, otherwise assumes string
     * @param valueClass to use for the value
     * @param ibdo to set the parameter on
     * @param ibdoMethodName method name to use (e.g. setFontEncoding)
     * @param parameter value to use
     * @param element to get the name from
     */
    private static void setParameterOnIbdo(final Class<?> keyClass, final Class<?> valueClass,
            final IBaseDataObject ibdo, final String ibdoMethodName, final Object parameter, final Element element) {
        try {
            if (keyClass == null) {
                final Method method = IBaseDataObject.class.getDeclaredMethod(ibdoMethodName, valueClass);

                method.invoke(ibdo, parameter);
            } else {
                final String name = (String) stringDecoder.decode(element.getChild(NAME_ELEMENT_NAME));
                final Method method = IBaseDataObject.class.getDeclaredMethod(ibdoMethodName, keyClass, valueClass);

                method.invoke(ibdo, name, parameter);
            }
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            LOGGER.warn("Unable to call ibdo method {}!", ibdoMethodName, e);
        }
    }

    /**
     * Creates an XML string from a parent IBaseDataObject and a list of children IBaseDataObjects.
     * 
     * @param parent the parent IBaseDataObject
     * @param children the children IBaseDataObjects.
     * @param initialIbdo the initial IBaseDataObject.
     * @return the XML string.
     */
    public static String xmlFromIbdo(final IBaseDataObject parent, final List<IBaseDataObject> children,
            final IBaseDataObject initialIbdo) {
        Validate.notNull(parent, "Required: parent != null!");
        Validate.notNull(children, "Required: children != null!");
        Validate.notNull(initialIbdo, "Required: initialIbdo != null!");

        final Element rootElement = new Element(RESULT_ELEMENT_NAME);
        final Element setupElement = new Element(SETUP_ELEMENT_NAME);

        rootElement.addContent(setupElement);

        xmlFromIbdoMainElements(initialIbdo, setupElement);

        final Element answersElement = new Element(ANSWERS_ELEMENT_NAME);

        rootElement.addContent(answersElement);

        xmlFromIbdoMainElements(parent, answersElement);

        final List<IBaseDataObject> extractedRecords = parent.getExtractedRecords();
        if (extractedRecords != null) {
            for (int i = 0; i < extractedRecords.size(); i++) {
                final IBaseDataObject extractedRecord = extractedRecords.get(i);
                final Element extractElement = new Element(EXTRACTED_RECORD_ELEMENT_PREFIX + (i + 1));

                xmlFromIbdoMainElements(extractedRecord, extractElement);

                answersElement.addContent(extractElement);
            }
        }

        for (int i = 0; i < children.size(); i++) {
            final IBaseDataObject child = children.get(i);
            final Element childElement = new Element(ATTACHMENT_ELEMENT_PREFIX + (i + 1));

            xmlFromIbdoMainElements(child, childElement);

            answersElement.addContent(childElement);
        }

        return AbstractJDOMUtil.toString(new Document(rootElement));
    }

    /**
     * Creates xml from the IBaseDataObject excluding the extracted records and children.
     * 
     * @param ibdo to create xml from.
     * @param element to add the xml to.
     */
    public static void xmlFromIbdoMainElements(final IBaseDataObject ibdo, final Element element) {
        addNonNullContent(element, DATA_ELEMENT_NAME, ibdo.getChannelFactory());
        addNonDefaultContent(element, BIRTH_ORDER_ELEMENT_NAME, ibdo.getBirthOrder());
        addNonNullContent(element, BROKEN_ELEMENT_NAME, ibdo.getBroken());
        addNonNullContent(element, CLASSIFICATION_ELEMENT_NAME, ibdo.getClassification());

        final int childCount = element.getChildren().size();
        for (final String currentForm : ibdo.getAllCurrentForms()) {
            element.addContent(childCount, protectedElement(CURRENT_FORM_ELEMENT_NAME, currentForm));
        }

        addNonNullContent(element, FILENAME_ELEMENT_NAME, ibdo.getFilename());
        addNonNullContent(element, FONT_ENCODING_ELEMENT_NAME, ibdo.getFontEncoding());
        addNonNullContent(element, FOOTER_ELEMENT_NAME, ibdo.footer());
        addNonNullContent(element, HEADER_ELEMENT_NAME, ibdo.header());
        addNonNullContent(element, HEADER_ENCODING_ELEMENT_NAME, ibdo.getHeaderEncoding());
        addNonNullContent(element, ID_ELEMENT_NAME, ibdo.getId());
        addNonDefaultContent(element, NUM_CHILDREN_ELEMENT_NAME, ibdo.getNumChildren());
        addNonDefaultContent(element, NUM_SIBLINGS_ELEMENT_NAME, ibdo.getNumSiblings());
        addNonDefaultContent(element, OUTPUTABLE_ELEMENT_NAME, ibdo.isOutputable());
        addNonDefaultContent(element, PRIORITY_ELEMENT_NAME, ibdo.getPriority());

        final String processingError = ibdo.getProcessingError();
        final String fixedProcessingError = processingError == null ? null
                : processingError.substring(0, processingError.length() - 1);
        addNonNullContent(element, PROCESSING_ERROR_ELEMENT_NAME, fixedProcessingError);

        addNonNullContent(element, TRANSACTION_ID_ELEMENT_NAME, ibdo.getTransactionId());
        addNonNullContent(element, WORK_BUNDLE_ID_ELEMENT_NAME, ibdo.getWorkBundleId());

        for (final Entry<String, Collection<Object>> parameter : ibdo.getParameters().entrySet()) {
            for (final Object item : parameter.getValue()) {
                final Element metaElement = new Element(PARAMETER_ELEMENT_NAME);

                element.addContent(metaElement);
                metaElement.addContent(preserve(protectedElement(NAME_ELEMENT_NAME, parameter.getKey())));
                metaElement.addContent(preserve(protectedElement(VALUE_ELEMENT_NAME, item.toString())));
            }
        }

        for (final Entry<String, byte[]> view : ibdo.getAlternateViews().entrySet()) {
            final Element metaElement = new Element(VIEW_ELEMENT_NAME);

            element.addContent(metaElement);
            metaElement.addContent(preserve(protectedElement(NAME_ELEMENT_NAME, view.getKey())));
            metaElement.addContent(preserve(protectedElement(VALUE_ELEMENT_NAME, view.getValue())));
        }
    }

    private static void addNonNullContent(final Element parent, final String elementName, final String string) {
        if (string != null) {
            parent.addContent(preserve(protectedElement(elementName, string)));
        }
    }

    private static void addNonNullContent(final Element parent, final String elementName, final byte[] bytes) {
        if (bytes != null) {
            parent.addContent(preserve(protectedElement(elementName, bytes)));
        }
    }

    private static void addNonNullContent(final Element parent, final String elementName,
            final SeekableByteChannelFactory seekableByteChannelFactory) {
        if (seekableByteChannelFactory != null) {
            try {
                final byte[] bytes =
                        SeekableByteChannelHelper.getByteArrayFromChannel(seekableByteChannelFactory, BaseDataObject.MAX_BYTE_ARRAY_SIZE);

                addNonNullContent(parent, elementName, bytes);
            } catch (final IOException e) {
                LOGGER.error("Could not get bytes from SeekableByteChannel!", e);
            }
        }
    }

    private static void addNonDefaultContent(final Element parent, final String elementName, final boolean bool) {
        if (((Boolean) PRIMITVE_NAME_DEFAULT_MAP.get(elementName)).booleanValue() != bool) {
            parent.addContent(AbstractJDOMUtil.simpleElement(elementName, bool));
        }
    }

    private static void addNonDefaultContent(final Element parent, final String elementName, final int integer) {
        if (((Integer) PRIMITVE_NAME_DEFAULT_MAP.get(elementName)).intValue() != integer) {
            parent.addContent(AbstractJDOMUtil.simpleElement(elementName, integer));
        }
    }

    private static Element preserve(final Element element) {
        element.setAttribute("space", "preserve", XML_NAMESPACE);

        return element;
    }

    private static Element protectedElement(final String name, final String string) {
        return protectedElement(name, string.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a 'protected' element which can be encoded with base64 if it contains unsafe characters
     * 
     * See method source for specific definition of 'unsafe'.
     * 
     * @param name of the element
     * @param bytes to wrap, if they contain unsafe characters
     * @return the created element
     */
    private static Element protectedElement(final String name, final byte[] bytes) {
        final Element element = new Element(name);

        boolean badCharacters = false;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 9 || bytes[i] > 13 && bytes[i] < 32) {
                badCharacters = true;
                break;
            }
        }
        if (badCharacters) {
            final StringBuilder base64String = new StringBuilder();
            base64String.append(BASE64_NEW_LINE_STRING);
            base64String.append(BASE64_ENCODER.encodeToString(bytes));
            base64String.append(BASE64_NEW_LINE_STRING);

            element.setAttribute(ENCODING_ATTRIBUTE_NAME, BASE64_ATTRIBUTE_NAME);
            element.addContent(base64String.toString());
        } else {
            element.addContent(new String(bytes, StandardCharsets.ISO_8859_1));
        }

        return element;
    }

}
