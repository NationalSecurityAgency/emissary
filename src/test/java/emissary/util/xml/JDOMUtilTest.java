package emissary.util.xml;

import emissary.test.core.junit5.UnitTest;

import org.jdom2.Document;
import org.jdom2.input.JDOMParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("IdentifierName")
class JDOMUtilTest extends UnitTest {

    static final String PLIST_XML =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                       <key>Label</key>
                       <string>com.apple.AOSNotification</string>
                       <key>Program</key>
                       <string>/usr/sbin/aosnotifyd</string>
                       <key>MachServices</key>
                       <dict>
                               <key>com.apple.AOSNotification</key>
                               <dict>
                                       <key>ResetAtClose</key>
                                       <true/>
                               </dict>
                       </dict>
                       <key>WorkingDirectory</key>
                       <string>/tmp</string>
                       <key>Umask</key>
                       <integer>23</integer>
                    </dict>
                    </plist>
                    """;

    @Test
    void testNonValidatingDTDRetrievalSuppression() {
        try {
            final Document doc = JDOMUtil.createDocument(PLIST_XML, null, false);
            assertNotNull(doc, "Document should be created");
        } catch (Throwable t) {
            fail("An exception should not be thrown here, perhaps the DTD validation is not fully disabled.", t);
        }
    }

    @Test
    void testValidationFailedDTDRetrieval() {
        assertThrows(JDOMParseException.class, () -> JDOMUtil.createDocument(PLIST_XML, null, true));
    }
}
