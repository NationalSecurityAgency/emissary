package emissary.test.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A class that provides some tricky samples. These samples can be used in testing to make sure our code and the 3rd
 * party libraries we choose can handle unusual cases.
 * <p>
 * Each example contains detailed explanation and links to useful reference materials.
 */
public final class ComplexUnicodeSamples {

    private ComplexUnicodeSamples() {}

    /**
     * Returns a string that contains one graphical unit (in this case an emoji) that consists of 5 Unicode scalar values.
     * The user-perceived string would be one facepalming emoji. A user would expect hit the arrow key once to traverse the
     * cursor across this one emoji on the screen. The length of the UTF-8 encoded byte array is 17 bytes. One emoji, 17
     * UTF8 bytes.
     * <p>
     * SCALAR 1: First, there’s a base character that means a person face palming.
     * <p>
     * SCALAR 2: By default, the person would have a cartoonish yellow color. The next character is an emoji skintone
     * modifier the changes the color of the person’s skin (and, in practice, also the color of the person’s hair).
     * <p>
     * SCALAR 3 and 4: By default, the gender of the person is undefined, and e.g. Apple defaults to what they consider a
     * male appearance and e.g. Google defaults to what they consider a female appearance. The next two scalar values pick a
     * male-typical appearance specifically regardless of font and vendor. Instead of being an emoji-specific modifier like
     * the skin tone, the gender specification uses an emoji-predating gender symbol (MALE SIGN) explicitly ligated using
     * the ZERO WIDTH JOINER with the (skin-toned) face-palming person. (Whether it is a good or a bad idea that the skin
     * tone and gender specifications use different mechanisms is out of the scope of this post.)
     * <p>
     * SCALAR 5: Finally, VARIATION SELECTOR-16 makes it explicit that we want a multicolor emoji rendering instead of a
     * monochrome dingbat rendering.
     * 
     * @return the Java string containing this one facepalming dude emoji with a not-yellow skin tone.
     * 
     * @see ComplexUnicodeSamplesTest#demonstrateMetadataAboutFacePalmDude()
     * @see <a href="https://hsivonen.fi/string-length/">https://hsivonen.fi/string-length/</a>
     */
    public static String getFacePalmingMaleControlSkintone() {

        StringBuilder sb = new StringBuilder();

        // SCALAR 1: U+1F926 FACE PALM
        // Use the lookup for how to represent in java
        // https://www.fileformat.info/info/unicode/char/1f926/index.htm
        // UTF-32 code units: 1
        // UTF-16 code units: 2
        // UTF-8 code units: 4
        // UTF-32 bytes: 4
        // UTF-16 bytes: 4
        // UTF-8 bytes: 4
        sb.append("\uD83E\uDD26");

        // SCALAR 2: U+1F3FC EMOJI MODIFIER FITZPATRICK TYPE-3
        // https://www.fileformat.info/info/unicode/char/1f3fc/index.htm
        // UTF-32 code units: 1
        // UTF-16 code units: 2
        // UTF-8 code units: 4
        // UTF-32 bytes: 4
        // UTF-16 bytes: 4
        // UTF-8 bytes: 4
        sb.append("\uD83C\uDFFC");

        // SCALAR 3: U+200D ZERO WIDTH JOINER
        // UTF-32 code units: 1
        // UTF-16 code units: 1
        // UTF-8 code units: 3
        // UTF-32 bytes: 4
        // UTF-16 bytes: 2
        // UTF-8 bytes: 3
        sb.append("\u200D");

        // SCALAR 4: U+2642 MALE SIGN
        // UTF-32 code units: 1
        // UTF-16 code units: 1
        // UTF-8 code units: 3
        // UTF-32 bytes: 4
        // UTF-16 bytes: 2
        // UTF-8 bytes: 3
        sb.append("\u2642");

        // SCALAR 5: U+FE0F VARIATION SELECTOR-16
        // UTF-32 code units: 1
        // UTF-16 code units: 1
        // UTF-8 code units: 3
        // UTF-32 bytes: 4
        // UTF-16 bytes: 2
        // UTF-8 bytes: 3
        sb.append("\uFE0F");

        return sb.toString();
    }


    /**
     * This map is useful for testing that our code and any 3rd party XML library we are using is handling unicode within
     * XML correctly.
     * 
     * @return A map of strings where the key is the XML node containing an XML-escaped surrogate pair unicode value and the
     *         value is is the properly extracted java string value with un-escaped unicode strings.
     * @see <a href=
     *      "https://github.com/FasterXML/woodstox/pull/174/files">https://github.com/FasterXML/woodstox/pull/174/files</a>
     */
    public static Map<String, String> getXmlSamples() {
        // See https://github.com/FasterXML/woodstox/pull/174/files
        Map<String, String> xmlWithExp = new HashMap<String, String>();
        // Numeric surrogate pairs
        xmlWithExp.put("<root>surrogate pair: &#55356;&#57221;.</root>",
                "surrogate pair: \uD83C\uDF85.");
        // Hex and numeric surrogate pairs
        xmlWithExp.put("<root>surrogate pair: &#xD83C;&#57221;.</root>",
                "surrogate pair: \uD83C\uDF85.");
        // Numeric and hex surrogate pairs
        xmlWithExp.put("<root>surrogate pair: &#55356;&#xDF85;.</root>",
                "surrogate pair: \uD83C\uDF85.");
        // Hex surrogate pairs
        xmlWithExp.put("<root>surrogate pair: &#xD83C;&#xDF85;.</root>",
                "surrogate pair: \uD83C\uDF85.");
        // Two surrogate pairs
        xmlWithExp.put("<root>surrogate pair: &#55356;&#57221;&#55356;&#57220;.</root>",
                "surrogate pair: \uD83C\uDF85\uD83C\uDF84.");
        // Surrogate pair and simple entity
        xmlWithExp.put("<root>surrogate pair: &#55356;&#57221;&#8482;.</root>",
                "surrogate pair: \uD83C\uDF85\u2122.");

        return xmlWithExp;
    }

    /**
     * This will not work properly in versions of java earlier than Java 20.
     * <p>
     * Once we get to Java 20, this method should work properly.
     * <p>
     * Character boundary analysis allows users to interact with characters as they expect to, for example, when moving the
     * cursor through a text string. Character boundary analysis provides correct navigation through character strings,
     * regardless of how the character is stored. The boundaries returned may be those of supplementary characters,
     * combining character sequences, or ligature clusters. For example, an accented character might be stored as a base
     * character and a diacritical mark. What users consider to be a character can differ between languages.
     * 
     * @see <a href=
     *      "https://horstmann.com/unblog/2023-10-03/index.html">https://horstmann.com/unblog/2023-10-03/index.html</a> -
     *      Scroll to the section titled "Just Use Strings"
     *
     * @param text - the string to analyze.
     * @return the count of user-perceived graphemes as based on the character break iterator. In versions of java earlier
     *         than Java 20, this will not function as expected.
     */
    public static int countGraphemesUsingJavaBuiltInBreakIterator(String text) {

        java.text.BreakIterator breakIterator = java.text.BreakIterator.getCharacterInstance(Locale.getDefault());
        breakIterator.setText(text);

        int count = 0;
        for (int end = breakIterator.next(); end != java.text.BreakIterator.DONE; end = breakIterator.next()) {
            count++;
        }

        return count;
    }

    /**
     * Using the industry-standard ICU4J library provided by IBM.
     * <p>
     * NOTE: Updating the version of this library might change which unicode database is referenced for these calculations.
     * We should strive to keep this library as up-to-date as possible in both test and production source code.
     * 
     * @param text the string to analyze
     * @return a count of how many user-perceived glyphs/graphemes are present in the string. If you placed a cursor diretly
     *         to the left (or right for right-to-left string), and pressed the arrow key to traverse the string, how many
     *         times would you need to press the arrow key to traverse to the right-most end of the string (or leftmost for
     *         R-to-L strings).
     */
    public static int countGraphemesUsingIcu4J(String text) {
        com.ibm.icu.text.BreakIterator breakIterator = com.ibm.icu.text.BreakIterator.getCharacterInstance();
        breakIterator.setText(text);

        int count = 0;
        for (int end = breakIterator.next(); end != com.ibm.icu.text.BreakIterator.DONE; end = breakIterator.next()) {
            count++;
        }

        return count;
    }

}
