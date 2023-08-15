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
import emissary.kff.KffDataObjectHandler;
import emissary.util.PlaceComparisonHelper;
import emissary.util.io.ResourceReader;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static emissary.core.constants.IbdoXmlElementNames.ANSWERS;
import static emissary.core.constants.IbdoXmlElementNames.SETUP;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This class contains utility methods used by RegressionTest.
 */
public final class RegressionTestUtil {
    /**
     * XML builder to read XML answer file in
     */
    private static final SAXBuilder XML_BUILDER = new SAXBuilder(XMLReaders.NONVALIDATING);

    /**
     * Default configuration to only check data when comparing
     */
    private static final DiffCheckConfiguration DIFF_CHECK = DiffCheckConfiguration.onlyCheckData();

    /**
     * Logger instance
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RegressionTestUtil.class);

    /**
     * Open options for (over-)writing answers XML
     */
    private static final Set<StandardOpenOption> CREATE_WRITE_TRUNCATE = new HashSet<>(Arrays.asList(StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

    /**
     * test/resources folder
     */
    private static final Path TEST_RESX = getTestResx();

    private RegressionTestUtil() {}

    /**
     * Dynamically finds the src/test/resources directory to write the XML to.
     * 
     * Running locally in an IDE, PROJECT_BASE will likely point to {@code src/main/}
     * 
     * Running in Maven/Docker it will most likely point to {@code target/}
     */
    public static Path getTestResx() {
        // Gets us the parent folder to PROJECT_BASE
        Path pathBuilder = Paths.get(System.getenv("PROJECT_BASE"));
        // If in Docker, we need to go into src - we're probably already in it otherwise
        if (pathBuilder.endsWith("main")) { // Docker
            pathBuilder = pathBuilder.getParent();
        } else if (pathBuilder.getFileName().toString().contains("target")) {
            pathBuilder = pathBuilder.getParent().resolve("src");
        }
        // Append test/resources to finish the path off
        return pathBuilder.resolve("test/resources");
    }

    /**
     * @see ExtractionTest#checkAnswers(Document, IBaseDataObject, List, String)
     */
    public static void checkAnswers(final Document answers, final IBaseDataObject payload,
            final List<IBaseDataObject> attachments, final String placeName, final ElementDecoders decoders) {
        final Element root = answers.getRootElement();
        final Element parent = root.getChild(ANSWERS);

        assertNotNull(parent, "No 'answers' section found!");

        final List<IBaseDataObject> expectedAttachments = new ArrayList<>();
        final IBaseDataObject expectedIbdo = IBaseDataObjectXmlHelper.ibdoFromXml(answers, expectedAttachments, decoders);
        final String differences = PlaceComparisonHelper.checkDifferences(expectedIbdo, payload, expectedAttachments,
                attachments, placeName, DIFF_CHECK);

        assertNull(differences, differences);
    }

    /**
     * When generating XML answer files, we need to use the src version rather than target.
     * 
     * This method returns the XML file from that location.
     * 
     * @param resource to get the answer file for
     * @return the XML file in the src directory (not target)
     */
    public static Document getAnswerDocumentFor(final String resource) {
        try {
            final Path path = RegressionTestUtil.getXmlPath(resource);

            return path == null ? null : XML_BUILDER.build(path.toFile());
        } catch (final JDOMException | IOException e) {
            // Fail if invalid XML document
            fail(String.format("No valid answer document provided for %s", resource), e);
            return null;
        }
    }

    /**
     * Gets the XML filename/path for the given resource (a .dat file)
     * 
     * @param resource path to the .dat file
     * @return path to the corresponding .xml file
     */
    public static Path getXmlPath(final String resource) {
        final int datPos = resource.lastIndexOf(ResourceReader.DATA_SUFFIX);
        if (datPos == -1) {
            LOGGER.debug("Resource is not a DATA file {}", resource);
            return null;
        }

        final String xmlPath = resource.substring(0, datPos) + ResourceReader.XML_SUFFIX;
        return TEST_RESX.resolve(xmlPath);
    }

    /**
     * Generate the relevant XML and write to disk.
     * 
     * @param resource referencing the DAT file
     * @param initialIbdo for 'setup' section
     * @param finalIbdo for 'answers' section
     * @param encoders for encoding ibdo into XML
     * @param results for 'answers' section
     */
    public static void writeAnswerXml(final String resource, final IBaseDataObject initialIbdo, final IBaseDataObject finalIbdo,
            final List<IBaseDataObject> results, final ElementEncoders encoders) {
        // Generate the full XML (setup & answers from before & after)
        final String xmlContent = IBaseDataObjectXmlHelper.xmlFromIbdo(finalIbdo, results, initialIbdo, encoders);
        // Write out the XML to disk
        writeXml(resource, xmlContent);
    }

    /**
     * Helper method to write XML for a given DAT file.
     * 
     * @param resource referencing the DAT file
     * @param xmlContent to write to the XML answer file
     */
    public static void writeXml(final String resource, final String xmlContent) {
        final Path path = getXmlPath(resource);
        if (path == null) {
            fail(String.format("Could not get path for resource = %s", resource));
        }
        LOGGER.info("Writing answers file to path: {}", path);
        try (FileChannel fc = FileChannel.open(path, CREATE_WRITE_TRUNCATE);
                SeekableInMemoryByteChannel simbc = new SeekableInMemoryByteChannel(xmlContent.getBytes())) {
            fc.transferFrom(simbc, 0, simbc.size());
        } catch (final IOException ioe) {
            fail(String.format("Couldn't write XML answer file for resource: %s", resource), ioe);
        }
    }

    /**
     * Sets up the payload by resetting the payload to that from the XML
     * 
     * @see ExtractionTest#setupPayload(IBaseDataObject, Document)
     * @param payload the ibdo to reset
     * @param answers an XML Document object to set the ibdo payload to
     */
    public static void setupPayload(final IBaseDataObject payload, final Document answers, final ElementDecoders decoders) {
        final Element root = answers.getRootElement();

        if (root != null) {
            final Element parent = root.getChild(SETUP);

            if (parent != null) {
                payload.popCurrentForm(); // Remove default form put on by ExtractionTest.
                payload.setFileType(null); // Remove default filetype put on by ExtractionTest.
                // The only other fields set are data and filename.

                IBaseDataObjectXmlHelper.ibdoFromXmlMainElements(parent, payload, decoders);
            }
        }
    }

    /**
     * Use the 'default' case of {@link InitialFinalFormFormat} to get the initial form to satisfy
     * {@link ExtractionTest#testExtractionPlace(String)} picking this up before start
     * 
     * @param resource to get the form from
     * @return the initial form from the filename
     */
    public static String getInitialFormFromFilename(final String resource) {
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
     * Allow setting the internal data fields to null to avoid being included during serialisation.
     * 
     * @param initialIbdo to null out
     */
    public static void setDataToNull(final IBaseDataObject initialIbdo) {
        try {
            if (initialIbdo instanceof BaseDataObject) {
                IBaseDataObjectHelper.setPrivateFieldValue((BaseDataObject) initialIbdo, "theData", null);
                IBaseDataObjectHelper.setPrivateFieldValue((BaseDataObject) initialIbdo, "seekableByteChannelFactory", null);
            } else {
                fail("Didn't get an expected type of IBaseDataObject");
            }
        } catch (final IllegalAccessException | NoSuchFieldException e) {
            fail("Couldn't null out data for BDO", e);
        }
    }

    /**
     * Simple/default way to provide the initial IBDO
     * 
     * Takes the data from the dat file and sets the current (initial) form based on the filename
     * 
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    public static IBaseDataObject getInitialIbdoWithFormInFilename(final String resource, final KffDataObjectHandler kff) {
        try {
            final Path datFileUrl = Paths.get(new ResourceReader().getResource(resource).toURI());
            final InitialFinalFormFormat datFile = new InitialFinalFormFormat(datFileUrl);
            final SeekableByteChannelFactory sbcf = FileChannelFactory.create(datFile.getPath());
            // Create a BDO for the data, and set the filename correctly
            final IBaseDataObject initialIbdo = IBaseDataObjectXmlHelper.createStandardInitialIbdo(sbcf, "Classification",
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
     * Simple/default way to provide the final IBDO. Will modify the provided IBDO.
     * 
     * Sets the current (final) form based on the filename
     * 
     * @param resource path to the dat file
     * @param finalIbdo the existing final BDO after it's been processed by a place
     */
    public static void tweakFinalIbdoWithFormInFilename(final String resource, final IBaseDataObject finalIbdo) {
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
    }
}
