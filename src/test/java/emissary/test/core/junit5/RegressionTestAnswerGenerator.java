package emissary.test.core.junit5;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.place.IServiceProviderPlace;
import emissary.util.io.ResourceReader;

import com.google.errorprone.annotations.ForOverride;
import jakarta.annotation.Nullable;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

public class RegressionTestAnswerGenerator extends AnswerGenerator {

    protected static final Logger logger = LoggerFactory.getLogger(RegressionTestAnswerGenerator.class);

    @Override
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
    @Override
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

    @ForOverride
    @Override
    protected void tweakInitialIbdoBeforeSerialization(final String resource, final IBaseDataObject initialIbdo) {
        initialIbdo.clearData();
    }

    @Override
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
}
