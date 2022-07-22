package emissary.test.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import emissary.place.IServiceProviderPlace;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestExtractionTest extends UnitTest {

    @Test
    void testCheckStringValueForCollection() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
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
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assertions.assertNotNull(inputStream, "Could not locate: " + resourceName);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        Element meta = answerDoc.getRootElement().getChild("answers").getChild("meta");

        assertThrows(AssertionError.class, () -> test.checkStringValue(meta, "7;0;0;0;2;1", "testCheckStringValueForCollectionFailure"));
    }

    /**
     * Attempts to find filters. Adds given filter to filterList if found. Validates both individual filters return result,
     * as well as final filterList count.
     */
    @Test
    void testFindFilter() throws IOException {
        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        // verify boolean "result" from findFilter() is returning correctly
        assertTrue(test.findFilter("emissary.output.filter.DataFilter"), "DataFilter should be found.");
        assertTrue(test.findFilter("emissary.output.filter.JsonOutputFilter"), "JsonOutputFilter should be found.");
        assertFalse(test.findFilter("this.should.not.be.found"), "This filter should not be found.");
        assertFalse(test.findFilter("DataFilter"), "Should return false since path not provided.");

        // verify only found filters paths are added to filterList, should be 2 in this case
        assertEquals(2, test.filterList.size(), "filterList<InputStream> should have size 2.");
    }

    /**
     * No filter is added to filterList, so validation of meta names should FAIL.
     */
    @Test
    void testValidateFieldWithNoFilter() throws IOException, JDOMException {
        // Try to validate field with no filter, should FAIL
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        assertNotNull(inputStream, "Could not locate: " + resourceName);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        for (Element meta : children) {
            try {
                test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
            } catch (AssertionError e) {
                logger.info(e.toString());
                // ignore as this is expected.
            }
        }
    }

    /**
     * Filter is added to list to validate against, but filter does not validate meta names. This should FAIL.
     */
    @Test
    void testValidateFieldWithNonValidatingFilter() throws IOException, JDOMException {
        // Try to validate field with no filter, should FAIL
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        assertNotNull(inputStream, "Could not locate: " + resourceName);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        test.findFilter("emissary.output.filter.DataFilter");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        for (Element meta : children) {
            try {
                test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
            } catch (AssertionError e) {
                logger.info(e.toString());
                // ignore as this is expected.
            }
        }
    }

    /**
     * Passes multiple filter to findFilter() then tries to validate meta names against filters. Validates found filters
     * return true from findFilter(). Validates only filters that return true are added to filterList Validates
     * validateFieldInFilter does not fail when even one filter in list validates meta names
     *
     * this.filter.not.real: non-existent filter - XmlOutputFilter: real filter, will not validate - JsonOutputFilter: real
     * filter, will validate
     */
    @Test
    void testCheckValidateField() throws IOException, JDOMException {
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        assertNotNull(inputStream, "Could not locate: " + resourceName);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        // Add Filter to list to validate against
        assertFalse(test.findFilter("this.filter.not.real"));
        assertTrue(test.findFilter("emissary.output.filter.XmlOutputFilter"));
        assertTrue(test.findFilter("emissary.output.filter.JsonOutputFilter"));

        // verify only found filters paths are added to filterList, should be 2 in this case
        assertEquals(2, test.filterList.size(), "filterList should have size 2.");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        for (Element meta : children) {
            test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
        }
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
