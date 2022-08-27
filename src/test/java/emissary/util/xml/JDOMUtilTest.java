package emissary.util.xml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import emissary.test.core.junit5.UnitTest;
import org.jdom2.Document;
import org.jdom2.input.JDOMParseException;
import org.junit.jupiter.api.Test;

class JDOMUtilTest extends UnitTest {

    static final String plist_xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                    + "<plist version=\"1.0\">\n" + "<dict>\n" + "   <key>Label</key>\n" + "   <string>com.apple.AOSNotification</string>\n"
                    + "   <key>Program</key>\n" + "   <string>/usr/sbin/aosnotifyd</string>\n" + "   <key>MachServices</key>\n" + "   <dict>\n"
                    + "           <key>com.apple.AOSNotification</key>\n" + "           <dict>\n"
                    + "                   <key>ResetAtClose</key>\n" + "                   <true/>\n" + "           </dict>\n" + "   </dict>\n"
                    + "   <key>WorkingDirectory</key>\n" + "   <string>/tmp</string>\n" + "   <key>Umask</key>\n" + "   <integer>23</integer>\n"
                    + "</dict>\n" + "</plist>\n";

    @Test
    void testNonValidatingDTDRetrievalSuppression() {
        try {
            final Document doc = JDOMUtil.createDocument(plist_xml, null, false);
            assertNotNull(doc, "Document should be created");
        } catch (Throwable t) {
            fail("An exception should not be thrown here, perhaps the DTD validation is not fully disabled.", t);
        }
    }

    @Test
    void testValidationFailedDTDRetrieval() {
        assertThrows(JDOMParseException.class, () -> JDOMUtil.createDocument(plist_xml, null, true));
    }
}
