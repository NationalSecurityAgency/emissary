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

    @Test(expected = AssertionError.class)
    public void testValidateFieldWithFilterNotFound() throws IOException, JDOMException {
        // This passes a Burrito filter, should not be found (because we are only in Emissary), therefore should not be added to
        // list. This causes validation against no filter, should FAIL
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assert.assertNotNull("Could not locate: " + resourceName, inputStream);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");
        // Add Filter to list to validate against
        test.findFilter("LogFilter.cfg");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
            Element meta = children.get(i);
            test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
        }
    }

    @Test(expected = AssertionError.class)
    public void testCheckValidateFieldAgainstFilterThatDoesNotValidate() throws IOException, JDOMException {
        // DataFilter.cfg should not be able to validate the passed in .xml file
        // due to this, this test should FAIL
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        String resourceName = "/emissary/test/core/TestValidateFieldExtractionTest.xml";
        InputStream inputStream = TestExtractionTest.class.getResourceAsStream(resourceName);
        Assert.assertNotNull("Could not locate: " + resourceName, inputStream);
        Document answerDoc = builder.build(inputStream);
        inputStream.close();

        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");
        // Add Filter to list to validate against
        test.findFilter("DataFilter");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
            Element meta = children.get(i);
            test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
        }
    }

    /**
     * Validates meta fields in TestValidateFieldExtractionTest.xml Passes one fake filter, DataFilter which should not
     * validate, and JsonOutputFilter which should validate The test passes, with only DataFilter and JsonOutputFilter being
     * passed to FilterList to validate against
     * 
     * @throws IOException
     * @throws JDOMException
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
        test.findFilter("ThisFilterDoesNotExist");
        test.findFilter("DataFilter");
        test.findFilter("JsonOutputFilter.cfg");

        // put all children of <answers> into a List<> to loop through
        List<Element> children = answerDoc.getRootElement().getChild("answers").getChildren();
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
            Element meta = children.get(i);
            test.checkStringValue(meta, "1;2;3;4;5", "testCheckValidateField");
        }
    }

    /**
     * TestFindFilter, where multiple different filter names are passed (both real and nonexistenet) to make sure method
     * functions correctly
     * 
     * @throws IOException
     */
    @Test
    public void testFindFilter() throws IOException {
        WhyDoYouMakeMeDoThisExtractionTest test = new WhyDoYouMakeMeDoThisExtractionTest("nonsense");

        Assert.assertTrue("Filter expected to be found.", test.findFilter("JsonOutputFilter.cfg"));
        Assert.assertTrue("Filter expected to be found.", test.findFilter("DataFilter"));
        Assert.assertFalse("Filter expected to not exist.", test.findFilter("RandomFakeName.cfg"));
        Assert.assertFalse("Filter expected to not exist.", test.findFilter("DataFilterJsonOutputFilter"));
        Assert.assertFalse("Filter expected to not exist.", test.findFilter(""));
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
