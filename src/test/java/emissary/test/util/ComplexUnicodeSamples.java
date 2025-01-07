package emissary.test.util;

import java.util.HashMap;
import java.util.Map;

public class ComplexUnicodeSamples {

    /**
     * A utility method that will provide
     * <a href="https://en.wikipedia.org/wiki/Plane_(Unicode)#Basic_Multilingual_Plane">non-BMP</a> values inside an XML
     * element and the corresponding properly un-escaped values that should have been extracted for that element.
     * <p>
     * This map is useful for testing that any XML library we are using is handling unicode correctly.
     * 
     * @return A map of strings where the key is the XML node containing an XML-escaped surrogate pair unicode value and
     *         the value is is the properly extracted java string value.
     * @See <a href=
     *      "https://github.com/FasterXML/woodstox/pull/174/files">{@link https://github.com/FasterXML/woodstox/pull/174/files}</a>
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

}
