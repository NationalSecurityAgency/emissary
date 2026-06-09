package emissary.util.xml;

import emissary.test.core.junit5.UnitTest;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.JDOMParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("IdentifierName")
class SaferJDOMUtilTest extends UnitTest {

    @Test
    void testDTDDisallowed() {
        final String plistXml =
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <!DOCTYPE note SYSTEM "http://www.example.com/Note.dtd">
                        <note>
                        <to>To</to>
                        <from>From</from>
                        <heading>Reminder</heading>
                        </note>""";

        assertThrows(JDOMParseException.class, () -> SaferJDOMUtil.createDocument(plistXml));
    }

    @Test
    void testNonValidating() throws JDOMException {
        final String plistXml =
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <note>
                        <to>To</to>
                        <from>From</from>
                        <heading>Reminder</heading>
                        </note>""";

        final Document doc = SaferJDOMUtil.createDocument(plistXml);
        assertNotNull(doc, "Document should be created");
    }
}
