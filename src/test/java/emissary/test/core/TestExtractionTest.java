package emissary.test.core;

import emissary.place.IServiceProviderPlace;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TestExtractionTest extends UnitTest {

    @Test
    void testCheckStringValueForCollection() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assertions.assertNotNull(inputStream, "Could not locate: " + resourceName);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        Element meta = answerDoc.getRootElement().getChild("answers").getChild("meta");
        test.checkStringValue(meta, "1;2;3;4;5;6;7", "testCheckStringValueForCollection");
        test.checkStringValue(meta, "1;3;4;2;5;6;7", "testCheckStringValueForCollection");
        test.checkStringValue(meta, "1;3;2;4;7;5;6", "testCheckStringValueForCollection");
        test.checkStringValue(meta, "7;6;5;4;3;2;1", "testCheckStringValueForCollection");
    }

    @Test
    void testCheckStringValueForCollectionFailure() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assertions.assertNotNull(inputStream, "Could not locate: " + resourceName);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        Element meta = answerDoc.getRootElement().getChild("answers").getChild("meta");

        assertThrows(AssertionError.class, () -> test.checkStringValue(meta, "7;0;0;0;2;1", "testCheckStringValueForCollection"));
    }

    public static class WhyDoYouMakeMeDoThisExtractionTest extends ExtractionTest {

        public WhyDoYouMakeMeDoThisExtractionTest(String crazy) throws IOException {
            super(crazy);
        }

        @Override
        public IServiceProviderPlace createPlace() throws IOException {
            return null;
        }
    }
}
