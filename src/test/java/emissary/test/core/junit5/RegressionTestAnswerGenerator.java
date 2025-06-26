package emissary.test.core.junit5;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.core.IBaseDataObjectXmlHelper;
import emissary.place.IServiceProviderPlace;
import emissary.util.DisposeHelper;
import emissary.util.io.ResourceReader;

import com.google.errorprone.annotations.ForOverride;
import jakarta.annotation.Nullable;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.atomic.AtomicReference;

import static emissary.core.constants.IbdoXmlElementNames.ANSWERS;
import static org.junit.jupiter.api.Assertions.fail;

public class RegressionTestAnswerGenerator {

    protected static final Logger logger = LoggerFactory.getLogger(RegressionTestAnswerGenerator.class);

    /** Open options for (over-)writing answers XML */
    private static final Set<StandardOpenOption> CREATE_WRITE_TRUNCATE = new HashSet<>(Arrays.asList(StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

    /** test/resources folder */
    private static final Path TEST_RESX = getTestResx();

    /**
     * Dynamically finds the src/test/resources directory to write the XML to.
     * <p>
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
     * Actually generate the answer file for a given resource
     * <p>
     * Takes initial form and final forms from the filename
     *
     * @param resource to generate against
     */
    public void generateAnswerFiles(final String resource, final IServiceProviderPlace place, final IBaseDataObject initialIbdo,
            final ElementEncoders encoders, final AtomicReference<Class<?>> answerFileClassRef, final String logbackLoggerName) {
        try {
            // Clone the BDO to create an 'after' copy
            final IBaseDataObject finalIbdo = IBaseDataObjectHelper.clone(initialIbdo);
            // Actually process the BDO and keep the children
            final List<IBaseDataObject> finalResults;
            final List<LogbackTester.SimplifiedLogEvent> finalLogEvents;
            if (logbackLoggerName == null) {
                finalResults = place.agentProcessHeavyDuty(finalIbdo);
                finalLogEvents = new ArrayList<>();
            } else {
                try (LogbackTester logbackTester = new LogbackTester(logbackLoggerName)) {
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
            writeAnswerXml(resource, initialIbdo, finalIbdo, finalResults, finalLogEvents, encoders,
                    answerFileClassRef);
        } catch (final Exception e) {
            logger.error("Error running test {}", resource, e);
            fail("Unable to generate answer file", e);
        }
    }

    @Nullable
    public Document getAnswerDocumentFor(final String resource, final AtomicReference<Class<?>> answerFileClassRef) {
        try {
            /* XML builder to read XML answer file in */
            final Path path = getXmlPath(resource, answerFileClassRef);
            return path == null ? null : new SAXBuilder(XMLReaders.NONVALIDATING).build(path.toFile());
        } catch (final JDOMException | IOException e) {
            // Fail if invalid XML document
            fail(String.format("No valid answer document provided for %s", resource), e);
            return null;
        }
    }

    /**
     * Allow the initial IBDO to be overridden before serializing to XML.
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
     * In the default case, do nothing.
     *
     * @param resource path to the dat file
     * @param simplifiedLogEvents to tweak
     */
    @ForOverride
    protected void tweakFinalLogEventsBeforeSerialization(final String resource, final List<LogbackTester.SimplifiedLogEvent> simplifiedLogEvents) {
        // No-op unless overridden
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
            final List<IBaseDataObject> results, final List<LogbackTester.SimplifiedLogEvent> logEvents, final ElementEncoders encoders,
            @Nullable final AtomicReference<Class<?>> answerFileClassRef) {
        final Element rootElement = IBaseDataObjectXmlHelper.xmlElementFromIbdo(finalIbdo, results, initialIbdo, encoders);
        final Element answerElement = rootElement.getChild(ANSWERS);

        LogbackTester.SimplifiedLogEvent.toXml(logEvents).forEach(answerElement::addContent);

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

    /**
     * Default behavior to fix dispose runnables to change "variant" to "invariant"
     *
     * @param ibdo the base data object containing dispose runnables
     */
    public static void fixDisposeRunnables(final IBaseDataObject ibdo) {
        if (ibdo.hasParameter(DisposeHelper.KEY)) {
            final List<Object> values = ibdo.getParameter(DisposeHelper.KEY);
            final List<String> newValues = new ArrayList<>();

            for (Object o : values) {
                newValues.add(o.getClass().getName());
            }

            ibdo.putParameter(DisposeHelper.KEY, newValues);
        }
    }
}
