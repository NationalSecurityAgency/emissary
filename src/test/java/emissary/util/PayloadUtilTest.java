package emissary.util;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadUtilTest extends UnitTest {

    private static String timezone = "GMT";
    private static final String validFormCharsString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-)(/+";
    private static final Set<Character> validFormChars = new HashSet<>();

    @BeforeAll
    public static void setup() {
        // Needed to ensure correct output strings on the dates
        timezone = System.getProperty("user.timezone");
        System.setProperty("user.timezone", "GMT");

        validFormCharsString.chars().forEach(character -> validFormChars.add((char) character));
    }

    @AfterAll
    public static void teardown() {
        System.setProperty("user.timezone", timezone);
    }

    @Test
    void testOneLineString() {

        Instant now = Instant.now();

        // setup
        final String fn = "fname" + Family.SEP + "4";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.appendTransformHistory("BAR.BURP.BURPPLACE.http://example.com:1234/BurpPlace");
        d.setCreationTimestamp(now);
        final String expected = "att-4 FOO,BAR>>[UNKNOWN]//UNKNOWN//" + now;

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, true);

        // verify
        assertFalse(answer.contains("\n"), "Must be one line string");
        assertEquals(expected, answer, "Answer string did not equal the expected string");
    }

    @Test
    void testOneLineStringOnTLD() {
        Instant now = Instant.now();

        // setup
        final String fn = "fname";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.setCreationTimestamp(now);
        d.appendTransformHistory("BOGUSKEYELEMENT");
        final String expected = ">>[UNKNOWN]//UNKNOWN//" + now;

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, true);

        // verify
        assertFalse(answer.contains("\n"), "Must be one line string");
        assertEquals(expected, answer, "Answer string did not equal the expected string");
    }

    @Test
    void testMultiLineString() {

        Instant now = Instant.now();

        // setup
        final String fn = "fname" + Family.SEP + "4";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.setCreationTimestamp(now);

        // test
        final String answer = PayloadUtil.getPayloadDisplayString(d, false);

        // verify
        assertTrue(answer.contains("\n"), "Must be multi-line string");
        assertTrue(answer.contains("filename: fname-att-4"), "Answer did not contain the correct filename");
        assertTrue(answer.contains("creationTimestamp: " + now), "Answer did not contain the creationTimestamp");
        assertTrue(answer.contains("currentForms: [UNKNOWN]"), "Answer did not contain the currentForms");
        assertTrue(answer.contains("filetype: UNKNOWN"), "Answer did not contain the correct filetype");
        assertTrue(answer.contains("transform history (2)"), "Answer did not contain the transform history number");
        assertTrue(answer.contains("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace"),
                "Answer did not contain the correct transform history entry");
        assertTrue(answer.contains("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace"),
                "Answer did not contain the correct transform history entry");
    }

    @Test
    void testReducedHistory() {
        // setup
        final String fn = "testReducedHistory";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.appendTransformHistory("TEST.DROPOFFPLACE.http://example.com:1234/DropOffPlace");
        d.setCreationTimestamp(Instant.now());

        // test
        PayloadUtil.historyPreference.put("UNKNOWN", "REDUCED_HISTORY");
        final String answer = PayloadUtil.getPayloadDisplayString(d, false);
        PayloadUtil.historyPreference.clear();

        // verify
        assertTrue(answer.contains("\n"), "Must be multi-line string");
        assertTrue(answer.contains("filename: testReducedHistory"), "Answer did not contain the correct filename");
        assertTrue(answer.contains("transform history (3)"), "Answer did not contain the transform history number");
        assertFalse(answer.contains("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace"),
                "Answer should not contain this transform history entry");
        assertFalse(answer.contains("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace"),
                "Answer should not contain this transform history entry");
        assertTrue(answer.contains("** reduced transform history **"), "Answer should contain 'reduced transform history'");
        assertTrue(answer.contains("dropOff -> TEST.DROPOFFPLACE.http://example.com:1234/DropOffPlace"), "Answer should show dropoff");
    }

    @Test
    void testNoUrlHistory() {
        // setup
        final String fn = "noUrlHistory";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.setCreationTimestamp(Instant.now());

        // test
        PayloadUtil.historyPreference.put("UNKNOWN", "NO_URL");
        final String answer = PayloadUtil.getPayloadDisplayString(d, false);
        PayloadUtil.historyPreference.clear();

        // verify
        assertTrue(answer.contains("\n"), "Must be multi-line string");
        assertTrue(answer.contains("filename: noUrlHistory"), "Answer did not contain the correct filename");
        assertTrue(answer.contains("FOO.UNKNOWN.FOOPLACE"),
                "Answer should not contain the URL");
        assertFalse(answer.contains("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace"), "Answer should not contain full URL");
        assertTrue(answer.contains("BAR.UNKNOWN.BARPLACE"),
                "Answer should not contain the URL");
        assertFalse(answer.contains("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace"), "Answer should not contain full URL");
    }

    @Test
    void testHistoryDoesNotReduce() {
        // setup
        final String fn = "noMatch";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace");
        d.setCreationTimestamp(Instant.now());

        // test
        PayloadUtil.historyPreference.put("REDUCED", "REDUCED_HISTORY");
        PayloadUtil.historyPreference.put("NOURL", "NO_URL");
        final String answer = PayloadUtil.getPayloadDisplayString(d, false);
        PayloadUtil.historyPreference.clear();

        // verify
        assertTrue(answer.contains("\n"), "Must be multi-line string");
        assertTrue(answer.contains("filename: noMatch"), "Answer did not contain the correct filename");
        assertTrue(answer.contains("FOO.UNKNOWN.FOOPLACE.http://example.com:1234/FooPlace"),
                "Answer should not reduce history due to no matching form");
        assertTrue(answer.contains("BAR.UNKNOWN.BARPLACE.http://example.com:1234/BarPlace"),
                "Answer should not reduce history due to no matching form");
    }

    @Test
    void testNameOfSimpleObject() {
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), "ab/fn", Form.UNKNOWN);
        assertEquals("fn", PayloadUtil.getName(d), "Name of simple payload is shortname");
    }

    @Test
    void testNameOfCollection() {
        final List<IBaseDataObject> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), "ab/fn" + i, Form.UNKNOWN);
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
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), "testfile", Form.UNKNOWN);
        d.addAlternateView("AV", "def".getBytes());
        d.putParameter("P", "ghi");
        d.addProcessingError("jkl");
        d.setHeader("mno".getBytes());
        d.setFooter("pqr".getBytes());
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
        assertTrue(PayloadUtil.isValidForm(alphaLow), "Lower case alpha characters are expected to be valid");
        assertTrue(PayloadUtil.isValidForm(alphaLow.toUpperCase(Locale.getDefault())), "Upper case alpha characters are expected to be valid");
        assertTrue(PayloadUtil.isValidForm("0123456789"), "Numeric characters are expected to be valid");
        assertTrue(PayloadUtil.isValidForm("-_"), "'-' and '_' are expected to be valid form characters");
        assertTrue(PayloadUtil.isValidForm("formName-(suffixInParens)"), "Parentheses are expected to be valid form characters");
        assertTrue(PayloadUtil.isValidForm("formName-(application/xml)"), "'/' is expected to be a valid form character");
        assertFalse(PayloadUtil.isValidForm("."), "Dot should not be considered a valid form character");
        assertFalse(PayloadUtil.isValidForm(" "), "Space should not be considered a valid form character");
        assertTrue(PayloadUtil.isValidForm("+"), "'+' is expected to be a valid form character");

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

    @Test
    void testCompactHistory() {
        // setup
        final String fn = "noMatch";
        final IBaseDataObject d = DataObjectFactory.getInstance("abc".getBytes(), fn, Form.UNKNOWN);
        d.appendTransformHistory("FOO.PLACE_ONE.FOOPLACE.http://example.com:1234/FooPlace");
        d.appendTransformHistory("BAR.PLACE_TWO.BARPLACE.http://example.com:1234/BarPlace");
        d.appendTransformHistory("BAR.PLACE_THREE.NONEPLACE.http://example.com:1234/NonePlace", true);
        d.setCreationTimestamp(Instant.now());

        // test
        PayloadUtil.compactHistory = true;
        final String answer = PayloadUtil.getPayloadDisplayString(d);
        PayloadUtil.compactHistory = false;

        // verify
        assertTrue(answer.contains("transform history (3)"), "Answer history count is wrong");
        assertTrue(answer.contains("FOO.FOOPLACE: FooPlace"), "Answer should have compacted history");
        assertTrue(answer.contains("BAR.BARPLACE: BarPlace(NonePlace)"), "Answer should have compacted history");
    }

    /**
     * Compares the form to a set of valid characters
     * <p>
     * This implementation is marginally faster than the regex implementation, but is less maintainable
     *
     * @param form The form to be tested
     * @return Whether the form is considered valid
     */
    private static boolean isValidFormSetImplementation(String form) {
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
