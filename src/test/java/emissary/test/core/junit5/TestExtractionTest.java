package emissary.test.core.junit5;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.spy;

class TestExtractionTest extends UnitTest {

    private final String RESOURCE_NAME = "/emissary/test/core/junit5/TestExtractionTest.xml";
    private final String MISSING_TAGS_RESOURCE = "/emissary/test/core/junit5/MissingTags.xml";

    @Test
    void testCheckStringValueForCollection() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(RESOURCE_NAME);
        assertNotNull(inputStream, "Could not locate: " + RESOURCE_NAME);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        ExtractionTest test = spy(ExtractionTest.class);

        Element meta = answerDoc.getRootElement().getChild("answers").getChild("meta");
        test.checkStringValue(meta, "1;2;3;4;5;6;7", "testCheckStringValueForCollection");
        test.checkStringValue(meta, "1;3;4;2;5;6;7", "testCheckStringValueForCollection");
        test.checkStringValue(meta, "1;3;2;4;7;5;6", "testCheckStringValueForCollection");
        test.checkStringValue(meta, "7;6;5;4;3;2;1", "testCheckStringValueForCollection");
    }

    @Test
    void testCheckStringValueForCollectionFailure() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(RESOURCE_NAME);
        assertNotNull(inputStream, "Could not locate: " + RESOURCE_NAME);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        ExtractionTest test = spy(ExtractionTest.class);

        Element meta = answerDoc.getRootElement().getChild("answers").getChild("meta");

        assertThrows(AssertionError.class, () -> test.checkStringValue(meta, "7;0;0;0;2;1", "testCheckStringValueForCollection"));
    }

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
    void testCheckStringValueBangIndex() throws IOException, JDOMException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(RESOURCE_NAME);
        assertNotNull(inputStream, "Could not locate: " + RESOURCE_NAME);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();
        String matchMode = "!index";

        ExtractionTest test = spy(ExtractionTest.class);

        List<Element> dataList = answerDoc.getRootElement().getChild("answers").getChildren("data");
        Element bangIndexData = getAttributeFromDataChild(dataList, matchMode);

        test.checkStringValue(bangIndexData, "time:20221229", "testCheckStringValue!IndexTrue");
        assertThrows(AssertionError.class, () -> test.checkStringValue(bangIndexData, "timestamp:20221229", "testCheckStringValue!IndexFalse"));

        matchMode = "!contains";

        Element bangContainsData = getAttributeFromDataChild(dataList, matchMode);

        test.checkStringValue(bangContainsData, "time:20221229", "testCheckStringValue!ContainsTrue");
        assertThrows(AssertionError.class, () -> test.checkStringValue(bangContainsData, "timestamp:20221229", "testCheckStringValue!ContainsFalse"));
    }

    private Element getAttributeFromDataChild(List<Element> dataList, String matchMode) {
        Element data = null;
        // Having different matchModes in the same data necessitates having to go through each child and
        // filter for the correct one
        try {
            data = dataList.stream().filter(item -> item.getAttribute("matchMode").getValue().equals(matchMode)).findFirst().get();
        } catch (NoSuchElementException e) {
            fail("Attribute " + matchMode + " does not exist in data");
        }
        return data;
    }

    @Test
    void testExtractionNoNameTags() throws JDOMException, IOException {
        IBaseDataObject d = DataObjectFactory.getInstance();
        ExtractionTest test = spy(ExtractionTest.class);

        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(MISSING_TAGS_RESOURCE);
        Document doc = builder.build(inputStream);
        Element answers = doc.getRootElement().getChild("answers");

        // ensure that the test fails if the meta or nometa tags are not created correctly and a name tag is missing
        Assertions.assertThrows(AssertionError.class, () -> {
            test.checkAnswers(answers, d, null, MISSING_TAGS_RESOURCE);
        }, "The test should fail if we did not create the nometa tag correctly");
    }
}
