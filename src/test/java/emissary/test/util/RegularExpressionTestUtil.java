package emissary.test.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for testing regular expressions.
 */
public class RegularExpressionTestUtil {
    protected static Logger logger = LoggerFactory.getLogger(RegularExpressionTestUtil.class);

    /**
     * A method to test a list of values that should and should not match a particular regular expression. One of the
     * two lists may be empty, but not both.
     * 
     * @param regexPatternString - Required. The string to compile into regular expression and then test against the
     *        provided values. Must not be null or empty. Will throw a runtime exception if the regex syntax is
     *        improper.
     * @param shouldMatch - Optional. The list of strings that should match the regular expression.
     * @param shouldNotMatch - Optional. The list of strings that should not match the regular expression.
     */
    public static void testRegexPattern(String regexPatternString, List<String> shouldMatch, List<String> shouldNotMatch) {

        if (regexPatternString.isBlank()) {
            fail("The test lacks required parameters.");
        }

        Pattern subjectUnderTest = Pattern.compile(regexPatternString);

        testRegexPattern(subjectUnderTest, shouldMatch, shouldNotMatch);

    }

    /**
     * A method to test a list of values that should and should not match a particular regular expression. One of the
     * two lists may be empty, but not both.
     * 
     * @param patternUnderTest - Required. The pre-compiled pattern used to test against the provided values. Must not
     *        be null.
     * @param shouldMatch - Optional. The list of strings that should match the regular expression.
     * @param shouldNotMatch - Optional. The list of strings that should not match the regular expression.
     */
    public static void testRegexPattern(Pattern patternUnderTest, List<String> shouldMatch, List<String> shouldNotMatch) {
        int fineGrainTestCount = 0;
        if (patternUnderTest == null || (shouldMatch.isEmpty() && shouldNotMatch.isEmpty())) {
            fail("The test lacks required parameters.");
        }
        for (String matchMe : shouldMatch) {
            Matcher mm = patternUnderTest.matcher(matchMe);
            assertTrue(mm.find(), String.format(" -- Pattern SHOULD match the regex [%s], but did not: %s", patternUnderTest.pattern(), matchMe));
            fineGrainTestCount++;
        }

        for (String dontMatchMe : shouldNotMatch) {
            Matcher dmm = patternUnderTest.matcher(dontMatchMe);
            assertFalse(dmm.find(),
                    String.format(" -- Pattern SHOULD NOT match the regex[%s], but did: %s", patternUnderTest.pattern(), dontMatchMe));
            fineGrainTestCount++;
        }
        if (fineGrainTestCount == 0) {
            fail("Didn't test anything.");
        } else {
            logger.debug("Successfully asserted {} regex test cases.", fineGrainTestCount);
        }
    }


}
