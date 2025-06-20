package emissary.test.core.junit5;

import emissary.core.DiffCheckConfiguration;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectXmlCodecs.ElementDecoders;
import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.core.IBaseDataObjectXmlHelper;
import emissary.core.channels.FileChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.LogbackTester.SimplifiedLogEvent;
import emissary.util.ByteUtil;
import emissary.util.PlaceComparisonHelper;
import emissary.util.io.ResourceReader;

import com.google.errorprone.annotations.ForOverride;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

import static emissary.core.IBaseDataObjectXmlCodecs.ALWAYS_SHA256_ELEMENT_ENCODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_DECODERS;
import static emissary.core.IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
import static emissary.core.constants.IbdoXmlElementNames.ANSWERS;
import static emissary.core.constants.IbdoXmlElementNames.SETUP;
import static emissary.test.core.junit5.RegressionTestAnswerGenerator.fixDisposeRunnables;
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

    /* Difference configuration to use when comparing IBDO's. */
    private static final DiffCheckConfiguration DIFF_CHECK = DiffCheckConfiguration.configure().enableData().enableKeyValueParameterDiff().build();

    private RegressionTestAnswerGenerator answerGenerator;

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

    @ForOverride
    protected RegressionTestAnswerGenerator createAnswerGenerator() {
        return new RegressionTestAnswerGenerator();
    }

    protected RegressionTestAnswerGenerator getAnswerGenerator() {
        if (answerGenerator == null) {
            answerGenerator = createAnswerGenerator();
        }
        return answerGenerator;
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

    @ParameterizedTest
    @MethodSource("data")
    @Override
    public void testExtractionPlace(final String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        if (generateAnswers()) {
            // Get the data and create a channel factory to it
            final IBaseDataObject initialIbdo = getInitialIbdo(resource);
            getAnswerGenerator().generateAnswerFiles(resource, place, initialIbdo, getEncoders(resource), super.answerFileClassRef,
                    getLogbackLoggerName());
        }

        // Run the normal extraction/regression tests
        super.testExtractionPlace(resource);
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
    protected Document getAnswerDocumentFor(final String resource) {
        // If generating answers, get the src version, otherwise get the normal XML file
        if (generateAnswers()) {
            return getAnswerGenerator().getAnswerDocumentFor(resource, answerFileClassRef);
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
}
