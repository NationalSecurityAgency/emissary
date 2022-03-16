package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class PayloadUtilTest extends UnitTest {

    private static String timezone = "GMT";
    private static String validFormCharsString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-)(";
    private static Set<Character> validFormChars = new HashSet<>();

    @BeforeClass
    public static void setup() {
        // Needed to ensure correct output strings on the dates
        timezone = System.getProperty("user.timezone");
        System.setProperty("user.timezone", "GMT");

        for (char character : validFormCharsString.toCharArray()) {
            validFormChars.add(character);
        }
    }

    @AfterClass
    public static void teardown() {
        System.setProperty("user.timezone", timezone);
    }

    @Test
    public void testOneLineString() {
        // setup
        final String fn = "fname" + Family.SEP + "4";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.appendTransformHistory("BAR.BURP.BURPPLACE.http://example.com:1234/BurpPlace");
        d.setCreationTimestamp(new Date(0));
        final String expected = "att-4 FOO,BAR>>[UNKNOWN]//UNKNOWN//" + (new Date(0)).toString();

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, true);

        // verify
        assertTrue("Must be one line string", answer.indexOf("\n") == -1);
        assertTrue("Answer string did not equal the expected string", expected.compareTo(answer) == 0);
    }

    @Test
    public void testOneLineStringOnTLD() {
        // setup
        final String fn = "fname";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.setCreationTimestamp(new Date(0));
        d.appendTransformHistory("BOGUSKEYELEMENT");
        final String expected = ">>[UNKNOWN]//UNKNOWN//" + (new Date(0)).toString();

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, true);

        // verify
        assertTrue("Must be one line string", answer.indexOf("\n") == -1);
        assertTrue("Answer string did not equal the expected string", expected.compareTo(answer) == 0);
    }

    @Test
    public void testMultiLineString() {
        // setup
        final String fn = "fname" + Family.SEP + "4";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.setCreationTimestamp(new Date(0));

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, false);

        // verify
        assertTrue("Must be multi-line string", answer.indexOf("\n") > -1);
        assertTrue("Answer did not contain the correct filename", answer.contains("filename: fname-att-4"));
        assertTrue("Answer did not contain the creationTimestamp", answer.contains("creationTimestamp: " + (new Date(0)).toString()));
        assertTrue("Answer did not contain the currentForms", answer.contains("currentForms: [UNKNOWN]"));
        assertTrue("Answer did not contain the correct filetype", answer.contains("filetype: UNKNOWN"));
        assertTrue("Answer did not contain the transform history number", answer.contains("transform history (2)"));
        assertTrue("Answer did not contain the correct transform history entry",
                answer.contains("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace"));
        assertTrue("Answer did not contain the correct transform history entry",
                answer.contains("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace"));
    }

    @Test
    public void testNameOfSimpleObject() {
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), "ab/fn", Form.UNKNOWN);
        assertEquals("Name of simple payload is shortname", "fn", PayloadUtil.getName(d));
    }

    @Test
    public void testNameOfCollection() {
        final List<IBaseDataObject> list = new ArrayList<IBaseDataObject>();
        for (int i = 0; i < 3; i++) {
            final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), "ab/fn" + i, Form.UNKNOWN);
            list.add(d);
        }
        assertEquals("Name of collection payload is shortname with count", "fn0(3)", PayloadUtil.getName(list));
    }

    @Test
    public void testNameOfEmptyCollection() {
        final List<IBaseDataObject> list = new ArrayList<IBaseDataObject>();
        assertEquals("Name of empty collection is class name", "java.util.ArrayList", PayloadUtil.getName(list));
    }

    @Test
    public void testNameOfBadArgument() {
        final String s = "foo";
        assertEquals("Name of unexpected argument is class name", s.getClass().getName(), PayloadUtil.getName(s));
    }

    @Test
    public void testXmlSerizliaztion() throws Exception {
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), "testfile", Form.UNKNOWN);
        d.addAlternateView("AV", "def".getBytes());
        d.putParameter("P", "ghi");
        d.addProcessingError("jkl");
        d.setHeader("mno".getBytes());
        d.setFooter("pqr".getBytes());
        d.appendTransformHistory("stu");

        final String xml = PayloadUtil.toXmlString(d);
        assertTrue("Xml serialization must include payload data", xml.indexOf("abc") > -1);
        assertTrue("Xml serialization must include av data", xml.indexOf("def") > -1);
        assertTrue("Xml serialization must include param data", xml.indexOf("ghi") > -1);
        assertTrue("Xml serialization must include error data", xml.indexOf("jkl") > -1);
        assertTrue("Xml serialization must include header data", xml.indexOf("mno") > -1);
        assertTrue("Xml serialization must include footer data", xml.indexOf("pqr") > -1);
        assertTrue("Xml serialization must include history data", xml.indexOf("stu") > -1);

        final List<IBaseDataObject> list = new ArrayList<IBaseDataObject>();
        list.add(d);
        final String lxml = PayloadUtil.toXmlString(list);
        assertTrue("Xml serialization must include payload data", lxml.indexOf("abc") > -1);
        assertTrue("Xml serialization must include av data", lxml.indexOf("def") > -1);
        assertTrue("Xml serialization must include param data", lxml.indexOf("ghi") > -1);
        assertTrue("Xml serialization must include error data", lxml.indexOf("jkl") > -1);
        assertTrue("Xml serialization must include header data", lxml.indexOf("mno") > -1);
        assertTrue("Xml serialization must include footer data", lxml.indexOf("pqr") > -1);
        assertTrue("Xml serialization must include history data", lxml.indexOf("stu") > -1);
    }

    @Test
    public void testIsValidForm() {
        // Check that all expected valid characters are valid
        String alphaLow = "abcdefghijklmnopqrstuvwxyz";
        assertTrue("Lower case alpha characters are not considered valid", PayloadUtil.isValidForm(alphaLow));
        assertTrue("Upper case alpha characters are not considered valid", PayloadUtil.isValidForm(alphaLow.toUpperCase()));
        assertTrue("Numeric characters are not considered valid", PayloadUtil.isValidForm("0123456789"));
        assertTrue("Dash and underscore aren't considered valid", PayloadUtil.isValidForm("-_"));
        assertTrue("Parentheses aren't considered valid", PayloadUtil.isValidForm("formName-(suffixInParens)"));
        assertFalse("Dot isn't considered valid", PayloadUtil.isValidForm("."));
        assertFalse("Space isn't considered valid", PayloadUtil.isValidForm(" "));

        // Cycle through all characters and see how many are valid and that we have the expected number
        int validChars = 0;
        for (int i = 0; i < Character.MAX_VALUE; i++) {
            if (PayloadUtil.isValidForm(Character.toString((char) i))) {
                validChars++;
            }
        }
        assertEquals("Unexpected number of valid characters.", validFormChars.size(), validChars);

        // Create a set with possible form characters for randomly generated cases
        Set<Character> formChars = new HashSet<>(validFormChars);
        // Add an invalid character to generate some false cases
        formChars.add('.');

        Character[] formCharArray = new Character[formChars.size()];
        formChars.toArray(formCharArray);
        // Seed the random for consistent output
        Random rand = new Random(0);
        // Generate N example forms and test if set and regex implementation produce identical results
        for (int i = 0; i < 4000000; i++) {
            StringBuilder word = new StringBuilder();
            int size = rand.nextInt(20);
            for (int n = 0; n < size; n++) {
                word.append(formCharArray[rand.nextInt(formChars.size())]);
            }
            String form = word.toString();
            assertEquals("Regex and Set implementations of form check differ for form \"" + form + "\"", PayloadUtil.isValidForm(form),
                    isValidFormSetImplementation(form));
        }
    }

    /**
     * Compares the form to a set of valid characters
     *
     * This implementation is marginally faster than the regex implementation, but is less maintainable
     *
     * @param form The form to be tested
     * @return Whether the form is considered valid
     */
    private boolean isValidFormSetImplementation(String form) {
        if (form.length() > 0) {
            for (int i = 0; i < form.length(); i++) {
                if (!validFormChars.contains(form.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
