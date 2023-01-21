package emissary.util;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PayloadUtilTest extends UnitTest {

    private static String timezone = "GMT";
    private static final String validFormCharsString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-)(/";
    private static final Set<Character> validFormChars = new HashSet<>();

    @BeforeAll
    public static void setup() {
        // Needed to ensure correct output strings on the dates
        timezone = System.getProperty("user.timezone");
        System.setProperty("user.timezone", "GMT");

        for (char character : validFormCharsString.toCharArray()) {
            validFormChars.add(character);
        }
    }

    @AfterAll
    public static void teardown() {
        System.setProperty("user.timezone", timezone);
    }

    @Test
    void testOneLineString() {
        // setup
        final String fn = "fname" + Family.SEP + "4";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(UTF_8), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.appendTransformHistory("BAR.BURP.BURPPLACE.http://example.com:1234/BurpPlace");
        d.setCreationTimestamp(new Date(0));
        final String expected = "att-4 FOO,BAR>>[UNKNOWN]//UNKNOWN//" + (new Date(0));

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, true);

        // verify
        assertFalse(answer.contains("\n"), "Must be one line string");
        assertEquals(expected, answer, "Answer string did not equal the expected string");
    }

    @Test
    void testOneLineStringOnTLD() {
        // setup
        final String fn = "fname";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(UTF_8), fn, Form.UNKNOWN);
        d.setCreationTimestamp(new Date(0));
        d.appendTransformHistory("BOGUSKEYELEMENT");
        final String expected = ">>[UNKNOWN]//UNKNOWN//" + (new Date(0));

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, true);

        // verify
        assertFalse(answer.contains("\n"), "Must be one line string");
        assertEquals(expected, answer, "Answer string did not equal the expected string");
    }

    @Test
    void testMultiLineString() {
        // setup
        final String fn = "fname" + Family.SEP + "4";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(UTF_8), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.setCreationTimestamp(new Date(0));

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, false);

        // verify
        assertTrue(answer.contains("\n"), "Must be multi-line string");
        assertTrue(answer.contains("filename: fname-att-4"), "Answer did not contain the correct filename");
        assertTrue(answer.contains("creationTimestamp: " + new Date(0)), "Answer did not contain the creationTimestamp");
        assertTrue(answer.contains("currentForms: [UNKNOWN]"), "Answer did not contain the currentForms");
        assertTrue(answer.contains("filetype: UNKNOWN"), "Answer did not contain the correct filetype");
        assertTrue(answer.contains("transform history (2)"), "Answer did not contain the transform history number");
        assertTrue(answer.contains("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace"),
                "Answer did not contain the correct transform history entry");
        assertTrue(answer.contains("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace"),
                "Answer did not contain the correct transform history entry");
    }

    @Test
    void testNameOfSimpleObject() {
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(UTF_8), "ab/fn", Form.UNKNOWN);
        assertEquals("fn", PayloadUtil.getName(d), "Name of simple payload is shortname");
    }

    @Test
    void testNameOfCollection() {
        final List<IBaseDataObject> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(UTF_8), "ab/fn" + i, Form.UNKNOWN);
            list.add(d);
        }
        assertEquals("fn0(3)", PayloadUtil.getName(list), "Name of collection payload is shortname with count");
    }

    @Test
    void testNameOfEmptyCollection() {
        final List<IBaseDataObject> list = new ArrayList<>();
        assertEquals("java.util.ArrayList", PayloadUtil.getName(list), "Name of empty collection is class name");
    }

    @Test
    void testNameOfBadArgument() {
        final String s = "foo";
        assertEquals(s.getClass().getName(), PayloadUtil.getName(s), "Name of unexpected argument is class name");
    }

    @Test
    void testXmlSerizliaztion() {
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(UTF_8), "testfile", Form.UNKNOWN);
        d.addAlternateView("AV", "def".getBytes(UTF_8));
        d.putParameter("P", "ghi");
        d.addProcessingError("jkl");
        d.setHeader("mno".getBytes(UTF_8));
        d.setFooter("pqr".getBytes(UTF_8));
        d.appendTransformHistory("stu");

        final String xml = PayloadUtil.toXmlString(d);
        assertTrue(xml.contains("abc"), "Xml serialization must include payload data");
        assertTrue(xml.contains("def"), "Xml serialization must include av data");
        assertTrue(xml.contains("ghi"), "Xml serialization must include param data");
        assertTrue(xml.contains("jkl"), "Xml serialization must include error data");
        assertTrue(xml.contains("mno"), "Xml serialization must include header data");
        assertTrue(xml.contains("pqr"), "Xml serialization must include footer data");
        assertTrue(xml.contains("stu"), "Xml serialization must include history data");

        final List<IBaseDataObject> list = new ArrayList<>();
        list.add(d);
        final String lxml = PayloadUtil.toXmlString(list);
        assertTrue(lxml.contains("abc"), "Xml serialization must include payload data");
        assertTrue(lxml.contains("def"), "Xml serialization must include av data");
        assertTrue(lxml.contains("ghi"), "Xml serialization must include param data");
        assertTrue(lxml.contains("jkl"), "Xml serialization must include error data");
        assertTrue(lxml.contains("mno"), "Xml serialization must include header data");
        assertTrue(lxml.contains("pqr"), "Xml serialization must include footer data");
        assertTrue(lxml.contains("stu"), "Xml serialization must include history data");
    }

    @Test
    void testIsValidForm() {
        // Check that all expected valid characters are valid
        String alphaLow = "abcdefghijklmnopqrstuvwxyz";
        assertTrue(PayloadUtil.isValidForm(alphaLow), "Lower case alpha characters are not considered valid");
        assertTrue(PayloadUtil.isValidForm(alphaLow.toUpperCase()), "Upper case alpha characters are not considered valid");
        assertTrue(PayloadUtil.isValidForm("0123456789"), "Numeric characters are not considered valid");
        assertTrue(PayloadUtil.isValidForm("-_"), "Dash and underscore aren't considered valid");
        assertTrue(PayloadUtil.isValidForm("formName-(suffixInParens)"), "Parentheses aren't considered valid");
        assertTrue(PayloadUtil.isValidForm("formName-(application/xml)"), "Slash aren't considered valid");
        assertFalse(PayloadUtil.isValidForm("."), "Dot isn't considered valid");
        assertFalse(PayloadUtil.isValidForm(" "), "Space isn't considered valid");

        // Cycle through all characters and see how many are valid and that we have the expected number
        int validChars = 0;
        for (int i = 0; i < Character.MAX_VALUE; i++) {
            if (PayloadUtil.isValidForm(Character.toString((char) i))) {
                validChars++;
            }
        }
        assertEquals(validFormChars.size(), validChars, "Unexpected number of valid characters.");

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
            assertEquals(PayloadUtil.isValidForm(form),
                    isValidFormSetImplementation(form),
                    "Regex and Set implementations of form check differ for form \"" + form + "\"");
        }
    }

    /**
     * Compares the form to a set of valid characters
     * <p>
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
