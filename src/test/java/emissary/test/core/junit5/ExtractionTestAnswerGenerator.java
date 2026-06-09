package emissary.test.core.junit5;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlCodecs;
import emissary.place.IServiceProviderPlace;

import jakarta.annotation.Nullable;
import org.jdom2.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

public class ExtractionTestAnswerGenerator extends AnswerGenerator {

    @Override
    public void generateAnswerFiles(final String resource, final IServiceProviderPlace place, final IBaseDataObject initialIbdo,
            final IBaseDataObjectXmlCodecs.ElementEncoders encoders, final AtomicReference<Class<?>> answerFileClassRef,
            final String logbackLoggerName) {
        generateAnswerFiles(resource, null, place, initialIbdo, encoders, answerFileClassRef, logbackLoggerName);
    }

    @Override
    public void generateAnswerFiles(final String datResource, @Nullable final String answerResource, final IServiceProviderPlace place,
            final IBaseDataObject initialIbdo, final IBaseDataObjectXmlCodecs.ElementEncoders encoders,
            final AtomicReference<Class<?>> answerFileClassRef,
            final String logbackLoggerName) {
        try {
            // Load the existing document to preserve the <setup> section
            final Document existingDoc = getAnswerDocumentFor(answerResource == null ? datResource : answerResource, answerFileClassRef);
            if (existingDoc == null) {
                // expecting an answer doc that may contain a setup block, look for the <root></root> tags at least
                throw new IllegalArgumentException("No valid answer document provided for " + datResource);
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
            tweakFinalIbdoBeforeSerialization(datResource, finalIbdo);
            tweakFinalResultsBeforeSerialization(datResource, finalResults);
            tweakFinalLogEventsBeforeSerialization(datResource, finalLogEvents);

            // Generate the full XML (setup & answers from before & after)
            writeAnswerXml(datResource, answerResource, existingDoc, initialIbdo, finalIbdo, finalResults, finalLogEvents, encoders,
                    answerFileClassRef);

        } catch (final Exception e) {
            logger.error("Error running extraction test {}", datResource, e);
            fail("Unable to generate extraction answer file", e);
        }
    }

    @Override
    protected void tweakFinalIbdoBeforeSerialization(final String resource, final IBaseDataObject finalIbdo) {
        fixDisposeRunnables(finalIbdo);
    }
}
