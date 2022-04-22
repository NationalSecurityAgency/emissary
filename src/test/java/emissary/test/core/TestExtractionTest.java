package emissary.test.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThrows(AssertionError.class, () -> test.checkStringValue(meta, "7;0;0;0;2;1", "testCheckStringValueForCollection"));
    }

    /**
     * Attempts to find filters. Adds given filter to filterList if found. Validates both individual filters return result,
     * as well as final filterList count.
     */
    @Test
    public void testFindFilter() throws IOException {
        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        // verify boolean "result" from findFilter() is returning correctly
        Assert.assertTrue("DataFilter should be found.", test.findFilter("emissary.output.filter.DataFilter"));
        Assert.assertTrue("JsonOutputFilter should be found.", test.findFilter("emissary.output.filter.JsonOutputFilter"));
        Assert.assertFalse("This filter should not be found.", test.findFilter("this.should.not.be.found"));
        Assert.assertFalse("Should return false since path not provided.", test.findFilter("DataFilter"));

        // verify only found filters paths are added to filterList, should be 2 in this case
        Assert.assertEquals("filterList<InputStream> should have size 2.", 2, test.filterList.size());
    }

    /**
     * No filter is added to filterList, so validation of meta names should FAIL.
     */
    @Test(expected = AssertionError.class)
    public void testValidateFieldWithNoFilter() throws IOException, JDOMException {
        // Try to validate field with no filter, should FAIL
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assert.assertNotNull("Could not locate: " + resourceName, inputStream);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
            Element meta = children.get(i);
            test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
        }
    }

    /**
     * Filter is added to list to validate against, but filter does not validate meta names. This should FAIL.
     */
    @Test(expected = AssertionError.class)
    public void testValidateFieldWithNonValidatingFilter() throws IOException, JDOMException {
        // Try to validate field with no filter, should FAIL
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assert.assertNotNull("Could not locate: " + resourceName, inputStream);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        test.findFilter("emissary.output.filter.DataFilter");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
            Element meta = children.get(i);
            test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
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
    public void testCheckValidateField() throws IOException, JDOMException {
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assert.assertNotNull("Could not locate: " + resourceName, inputStream);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        // Add Filter to list to validate against
        Assert.assertFalse(test.findFilter("this.filter.not.real"));
        Assert.assertTrue(test.findFilter("emissary.output.filter.XmlOutputFilter"));
        Assert.assertTrue(test.findFilter("emissary.output.filter.JsonOutputFilter"));

        // verify only found filters paths are added to filterList, should be 2 in this case
        Assert.assertEquals("filterList<InputStream> should have size 2.", 2, test.filterList.size());

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
            Element meta = children.get(i);
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
