package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.junit.Test;

@SuppressWarnings("deprecation")
// TODO: either remove these tests or test the new Parser
public class SessionProducerTest extends UnitTest {
    @Test
    public void testBasicSetup() throws Exception {
        SimpleParser parser = new SimpleParser("This is a test".getBytes());
        SessionProducer sp = new SessionProducer(parser, "UNKNOWN");
        IBaseDataObject payload = sp.getNextSession("name");
        assertEquals("Parser/Producer should create payload object with correct data", "This is a test", new String(payload.data()));
    }

    @Test
    public void testZoneAssignments() {
        SimpleParser parser = new SimpleParser("This is a test".getBytes());
        SessionProducer sp = new SessionProducer(parser, "UNKNOWN");
        DecomposedSession d = new DecomposedSession();
        d.setHeader("The Header".getBytes());
        d.setFooter("The Footer".getBytes());
        d.setData("The Data".getBytes());
        d.setClassification("Purple Nurple");
        d.addMetaData("foo", "bar");
        IBaseDataObject payload = sp.createAndLoadDataObject(d, "name");
        assertEquals("Incorrect header", "The Header", new String(payload.header()));
        assertEquals("Incorrect footer", "The Footer", new String(payload.footer()));
        assertEquals("Incorrect data", "The Data", new String(payload.data()));
        assertEquals("Incorrect classification", "Purple Nurple", payload.getClassification());
        assertEquals("Incorrect metadata", "bar", payload.getStringParameter("foo"));
        assertEquals("Incorrect name", "name", payload.shortName());
    }

    @Test
    public void testAlternateViewAssignment() {
        SimpleParser parser = new SimpleParser("This is a test".getBytes());
        SessionProducer sp = new SessionProducer(parser, "UNKNOWN");
        DecomposedSession d = new DecomposedSession();
        d.addMetaData("FOO", "BAR");
        d.addMetaData("ALT_VIEW_FOOVIEW", "This is the view data".getBytes());
        d.addMetaData("ALT_VIEW_BARVIEW", "This is string data for a view");
        IBaseDataObject payload = sp.createAndLoadDataObject(d, "name");
        assertNotNull("Alt view must be created from byte array", payload.getAlternateView("FOOVIEW"));
        assertNotNull("Alt veiw must be created from string", payload.getAlternateView("BARVIEW"));
        assertEquals("Byte array view data incorrect", "This is the view data", new String(payload.getAlternateView("FOOVIEW")));
        assertEquals("String view data incorrect", "This is string data for a view", new String(payload.getAlternateView("BARVIEW")));
        assertNull("View data must be removed from normal metadata", payload.getParameter("ALT_VIEW_FOOVIEW"));
        assertNull("View data must be removed from normal metadata", payload.getParameter("ALT_VIEW_BARVIEW"));
    }

}
