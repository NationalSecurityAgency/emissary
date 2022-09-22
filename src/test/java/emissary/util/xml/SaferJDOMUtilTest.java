package emissary.util.xml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import emissary.test.core.junit5.UnitTest;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.JDOMParseException;
import org.junit.jupiter.api.Test;

class SaferJDOMUtilTest extends UnitTest {

    @Test
    void testDTDDisallowed() {
        final String plist_xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<!DOCTYPE note SYSTEM \"http://www.example.com/Note.dtd\">\n"
                        + "<note>\n"
                        + "<to>To</to>\n"
                        + "<from>From</from>\n"
                        + "<heading>Reminder</heading>\n"
                        + "</note>";

        assertThrows(JDOMParseException.class, () -> SaferJDOMUtil.createDocument(plist_xml));
    }

    @Test
    void testNonValidating() throws JDOMException {
        final String plist_xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<note>\n"
                        + "<to>To</to>\n"
                        + "<from>From</from>\n"
                        + "<heading>Reminder</heading>\n"
                        + "</note>";

        final Document doc = SaferJDOMUtil.createDocument(plist_xml);
        assertNotNull(doc, "Document should be created");
    }
}
