package emissary.test.core.junit5;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlCodecs;
import emissary.core.IBaseDataObjectXmlHelper;
import emissary.place.IServiceProviderPlace;

import com.google.errorprone.annotations.ForOverride;
import org.jdom2.Document;
import org.jdom2.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

public class ExtractionTestAnswerGenerator extends RegressionTestAnswerGenerator {

    @Override
    public void generateAnswerFiles(final String resource, final IServiceProviderPlace place, final IBaseDataObject initialIbdo,
            final IBaseDataObjectXmlCodecs.ElementEncoders encoders, final AtomicReference<Class<?>> answerFileClassRef,
            final String logbackLoggerName) {
        try {
            // Load the existing document to preserve the <setup> section
            final Document existingDoc = getAnswerDocumentFor(resource, answerFileClassRef);
            if (existingDoc == null) {
                // Fallback to standard behavior if no file exists yet
                super.generateAnswerFiles(resource, place, initialIbdo, encoders, answerFileClassRef, logbackLoggerName);
                return;
            }

            // Process the BDO as normal
            final IBaseDataObject finalIbdo = IBaseDataObjectHelper.clone(initialIbdo);
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

            // Apply standard tweaks
            tweakFinalIbdoBeforeSerialization(resource, finalIbdo);
            tweakFinalResultsBeforeSerialization(resource, finalResults);
            tweakFinalLogEventsBeforeSerialization(resource, finalLogEvents);

            // Merge: Take existing <setup> and combine with new <answers>
            final Element existingRoot = existingDoc.getRootElement();
            final Element setupElement = existingRoot.getChild("setup");

            if (setupElement == null) {
                fail("Existing XML for " + resource + " does not contain a <setup> element.");
            }

            // Generate a fresh root using the helper (we pass null for initialIbdo to keep the answer section clean)
            final Element newFullRoot = IBaseDataObjectXmlHelper.xmlElementFromIbdo(finalIbdo, finalResults, initialIbdo, encoders);
            final Element newAnswerElement = newFullRoot.getChild("answers");

            // Add log events to the new answer section
            LogbackTester.SimplifiedLogEvent.toXml(finalLogEvents).forEach(newAnswerElement::addContent);

            // Construct the merged Document
            final Element mergedRoot = new Element(existingRoot.getName(), existingRoot.getNamespace());
            mergedRoot.addContent(setupElement.detach());
            mergedRoot.addContent(newAnswerElement.detach());

            // Write back to answer file
            final byte[] xmlContent = bytesFromDocument(new Document(mergedRoot));
            writeXml(resource, xmlContent, answerFileClassRef);

        } catch (final Exception e) {
            logger.error("Error running extraction test {}", resource, e);
            fail("Unable to generate extraction answer file", e);
        }
    }

    @ForOverride
    @Override
    protected void tweakInitialIbdoBeforeSerialization(final String resource, final IBaseDataObject initialIbdo) {
        initialIbdo.clearData();
    }

    @Override
    protected void tweakFinalIbdoBeforeSerialization(final String resource, final IBaseDataObject finalIbdo) {
        fixDisposeRunnables(finalIbdo);
    }
}
