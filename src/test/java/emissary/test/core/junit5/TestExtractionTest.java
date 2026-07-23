package emissary.test.core.junit5;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class TestExtractionTest extends UnitTest {

    private static final String RESOURCE_NAME = "/emissary/test/core/junit5/TestExtractionTest.xml";
    private static final String MISSING_TAGS_RESOURCE = "/emissary/test/core/junit5/MissingTags.xml";
    private static final String PROC_ERROR_RESOURCE = "/emissary/test/core/junit5/ProcessingErrorTest.xml";

    @Test
    void testCheckStringValueForFontEncoding() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(RESOURCE_NAME);
        assertNotNull(inputStream, "Could not locate: " + RESOURCE_NAME);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        String fontEncodingValue = answerDoc.getRootElement().getChild("answers").getChild("fontEncoding").getValue();
        assertEquals("UTF16", fontEncodingValue);
    }

    @Test
    void testExtractionNoNameTags() throws JDOMException, IOException {
        IBaseDataObject d = DataObjectFactory.getInstance();
        ExtractionTest test = spy(ExtractionTest.class);

        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(MISSING_TAGS_RESOURCE);
        Document doc = builder.build(inputStream);

        // ensure that the test fails if the meta or nometa tags are not created correctly and a name tag is missing
        assertThrows(AssertionError.class, () -> {
            test.checkAnswers(doc, d, null, MISSING_TAGS_RESOURCE);
        }, "The test should fail if we did not create the nometa tag correctly");
    }

    @Test
    void testProcessingErrorTag() throws IOException, JDOMException {
        IBaseDataObject d = DataObjectFactory.getInstance();
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(PROC_ERROR_RESOURCE);
        assertNotNull(inputStream, "Could not locate: " + PROC_ERROR_RESOURCE);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        ExtractionTest test = spy(ExtractionTest.class);
        test.place = mock(IServiceProviderPlace.class);

        // answer file is expecting 2 processing errors
        AssertionError error;

        // test with no processing error present in payload
        error = assertThrows(AssertionError.class, () -> test.checkAnswers(answerDoc, d, null, PROC_ERROR_RESOURCE),
                "Test should fail, no processing errors added to payload.");
        assertTrue(error.getMessage().contains("but got null"),
                "Verify correct error message (assertNotNull)");

        // add first processing error to payload, verify fails at assertEquals
        d.addProcessingError("test processing error 1");
        error = assertThrows(AssertionError.class, () -> test.checkAnswers(answerDoc, d, null, PROC_ERROR_RESOURCE),
                "Test should fail, only one processing error added to payload.");
        assertTrue(error.getMessage().contains(
                "Processing Error mismatch are not equal -> Expected: test processing error 1;test processing error 2;, Actual: test processing error 1;"),
                "Verify correct error message (assertEquals)");

        // add second procError, should pass through checkAnswers now
        d.addProcessingError("test processing error 2");
        test.checkAnswers(answerDoc, d, null, PROC_ERROR_RESOURCE);
    }
}
