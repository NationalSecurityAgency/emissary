package emissary.test.util;

import org.apache.commons.codec.Charsets;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A class that provides some tricky samples They can be used in testing to make sure our code and the 3rd party
 * libraries we choose can handle unusual cases.
 * <p>
 * Each example contains detailed explanation. and links to useful reference materials.
 */
public class ComplexUnicodeSamples {

    /**
     * Returns a string that contains one graphical unit consists of 5 Unicode scalar values.
     * <p>
     * First, there’s a base character that means a person face palming.
     * <p>
     * By default, the person would have a cartoonish yellow color. The next character is an emoji skintone modifier the
     * changes the color of the person’s skin (and, in practice, also the color of the person’s hair).
     * <p>
     * By default, the gender of the person is undefined, and e.g. Apple defaults to what they consider a male appearance
     * and e.g. Google defaults to what they consider a female appearance. The next two scalar values pick a male-typical
     * appearance specifically regardless of font and vendor. Instead of being an emoji-specific modifier like the skin
     * tone, the gender specification uses an emoji-predating gender symbol (MALE SIGN) explicitly ligated using the
     * 
     * ZERO WIDTH JOINER with the (skin-toned) face-palming person. (Whether it is a good or a bad idea that the skin tone
     * and gender specifications use different mechanisms is out of the scope of this post.)
     * <p>
     * Finally, VARIATION SELECTOR-16 makes it explicit that we want a multicolor emoji rendering instead of a monochrome
     * dingbat rendering.
     * 
     * @see #demonstrateMetaDataAboutTheseSamples() interesting observations about this sample
     * @see <a href="https://hsivonen.fi/string-length/">https://hsivonen.fi/string-length/</a>
     */
    public static String getFacePalmingMaleControlSkintone() {

        StringBuffer sb = new StringBuffer();

        // U+1F926 FACE PALM
        // Use the lookup for how to represent in java
        // https://www.fileformat.info/info/unicode/char/1f926/index.htm
        // UTF-32 code units: 1
        // UTF-16 code units: 2
        // UTF-8 code units: 4
        // UTF-32 bytes: 4
        // UTF-16 bytes: 4
        // UTF-8 bytes: 4
        sb.append("\uD83E\uDD26");

        // U+1F3FC EMOJI MODIFIER FITZPATRICK TYPE-3
        // https://www.fileformat.info/info/unicode/char/1f3fc/index.htm
        // UTF-32 code units: 1
        // UTF-16 code units: 2
        // UTF-8 code units: 4
        // UTF-32 bytes: 4
        // UTF-16 bytes: 4
        // UTF-8 bytes: 4
        sb.append("\uD83C\uDFFC");

        // U+200D ZERO WIDTH JOINER
        // UTF-32 code units: 1
        // UTF-16 code units: 1
        // UTF-8 code units: 3
        // UTF-32 bytes: 4
        // UTF-16 bytes: 2
        // UTF-8 bytes: 3
        sb.append("\u200D");

        // U+2642 MALE SIGN
        // UTF-32 code units: 1
        // UTF-16 code units: 1
        // UTF-8 code units: 3
        // UTF-32 bytes: 4
        // UTF-16 bytes: 2
        // UTF-8 bytes: 3
        sb.append("\u2642");

        // U+FE0F VARIATION SELECTOR-16
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
     * A utility method that will provide
     * <a href="https://en.wikipedia.org/wiki/Plane_(Unicode)#Basic_Multilingual_Plane">non-BMP</a> values inside an XML
     * element and the corresponding properly un-escaped values that should have been extracted for that element.
     * <p>
     * This map is useful for testing that any XML library we are using is handling unicode correctly.
     * 
     * @return A map of strings where the key is the XML node containing an XML-escaped surrogate pair unicode value and the
     *         value is is the properly extracted java string value.
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
     * Interesting observations
     * <p>
     * We’ve seen four different lengths so far:
     * 
     * <ul>
     * <li>Number of UTF-8 code units (17 in this case)</li>
     * <li>Number of UTF-16 code units (7 in this case)</li>
     * <li>Number of UTF-32 code units or Unicode scalar values (5 in this case)</li>
     * <li>Number of extended grapheme clusters (1 in this case)</li>
     * </ul>
     * Given a valid Unicode string and a version of Unicode, all of the above are well-defined and it holds that each item
     * higher on the list is greater or equal than the items lower on the list.
     * <p>
     * One of these is not like the others, though: The first three numbers have an unchanging definition for any valid
     * Unicode string whether it contains currently assigned scalar values or whether it is from the future and contains
     * unassigned scalar values as far as software written today is aware. Also, computing the first three lengths does not
     * involve lookups from the Unicode database. However, the last item depends on the Unicode version and involves lookups
     * from the Unicode database. If a string contains scalar values that are unassigned as far as the copy of the Unicode
     * database that the program is using is aware, the program will potentially overcount extended grapheme clusters in the
     * string compared to a program whose copy of the Unicode database is newer and has assignments for those scalar values
     * (and some of those assignments turn out to be combining characters).
     */
    @Test
    void demonstrateMetaDataAboutTheseSamples() {
        String facepalm = getFacePalmingMaleControlSkintone();

        assertEquals(17, facepalm.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(14, facepalm.getBytes(StandardCharsets.UTF_16BE).length);
        assertEquals(5, facepalm.getBytes(Charsets.toCharset("UTF-32")).length);

        assertEquals(5, facepalm.codePointCount(0, facepalm.length()));

        assertEquals(1, countGraphemes(facepalm));
    }


    public static int countGraphemes(String text) {
        BreakIterator breakIterator = BreakIterator.getCharacterInstance();
        breakIterator.setText(text);

        int count = 0;
        int start = breakIterator.first();
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            count++;
        }

        return count;
    }


}
