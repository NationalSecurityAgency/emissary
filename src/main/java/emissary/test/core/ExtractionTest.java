package emissary.test.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.kff.KffDataObjectHandler;
import emissary.place.IServiceProviderPlace;
import emissary.util.io.ResourceReader;
import emissary.util.xml.JDOMUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom2.Attribute;
import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public abstract class ExtractionTest extends UnitTest {

    protected static Logger logger = LoggerFactory.getLogger(ExtractionTest.class);
    protected KffDataObjectHandler kff = new KffDataObjectHandler(KffDataObjectHandler.TRUNCATE_KNOWN_DATA, KffDataObjectHandler.SET_FORM_WHEN_KNOWN,
            KffDataObjectHandler.SET_FILE_TYPE);
    protected IServiceProviderPlace place = null;
    private static final List<IBaseDataObject> NO_ATTACHMENTS = Collections.emptyList();

    private static final byte[] INCORRECT_VIEW_MESSAGE = "This is the incorrect view, the place should not have processed this view".getBytes();

    @Parameterized.Parameters
    public static Collection<?> data() {
        return getMyTestParameterFiles(ExtractionTest.class);
    }

    protected String resource;

    /**
     * Called by the Parameterized Runner
     */
    public ExtractionTest(String resource) throws IOException {
        super(resource);
        this.resource = resource;
    }

    @Before
    public void setUpPlace() throws Exception {
        place = createPlace();
    }

    /**
     * Derived classes must implement this
     */
    public abstract IServiceProviderPlace createPlace() throws IOException;

    @After
    public void tearDownPlace() {
        if (place != null) {
            place.shutDown();
            place = null;
        }
    }

    @Test
    public void testExtractionPlace() {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        // Need a pair consisting of a .dat file and a .xml file (answers)
        Document controlDoc = getAnswerDocumentFor(resource);
        if (controlDoc == null) {
            fail("No answers provided for test " + resource);
        }

        try (InputStream doc = new ResourceReader().getResourceAsStream(resource)) {
            byte[] data = new byte[doc.available()];
            doc.read(data);
            String defaultCurrentForm = resource.replaceAll("^.*/([^/@]+)(@\\d+)?\\.dat$", "$1");
            IBaseDataObject payload = DataObjectFactory.getInstance(data, resource, defaultCurrentForm);
            setupPayload(payload, controlDoc);
            processPreHook(payload, controlDoc);
            List<IBaseDataObject> attachments = place.agentProcessHeavyDuty(payload);
            processPostHook(payload, attachments);
            checkAnswersPreHook(controlDoc, payload, attachments, resource);
            checkAnswers(controlDoc, payload, attachments, resource);
            checkAnswersPostHook(controlDoc, payload, attachments, resource);
        } catch (Exception ex) {
            logger.error("Error running test {}", resource, ex);
            fail("Cannot run test " + resource + ": " + ex);
        }
    }

    protected void processPreHook(IBaseDataObject payload, Document controlDoc) {
        // Nothing to do here
    }

    protected void processPostHook(IBaseDataObject payload, List<IBaseDataObject> attachments) {
        // Nothing to do here
    }

    protected void checkAnswersPreHook(Document answers, IBaseDataObject payload, List<IBaseDataObject> attachments, String tname) {
        // Nothing to do here
    }

    protected void checkAnswersPreHook(Element answers, IBaseDataObject payload, IBaseDataObject attachment, String tname) {
        // Nothing to do here
    }

    protected void checkAnswersPostHook(Document answers, IBaseDataObject payload, List<IBaseDataObject> attachments, String tname) {
        // Nothing to do here
    }

    protected void checkAnswersPostHook(Element answers, IBaseDataObject payload, IBaseDataObject attachment, String tname) {
        // Nothing to do here
    }

    protected void checkAnswers(Document answers, IBaseDataObject payload, List<IBaseDataObject> attachments, String tname)
            throws DataConversionException {
        Element root = answers.getRootElement();
        Element parent = root.getChild("answers");
        if (parent == null) {
            parent = root;
        }

        // Check the payload
        checkAnswers(parent, payload, attachments, tname);

        // Check each attachment
        for (int attNum = 1; attNum <= attachments.size(); attNum++) {
            String atname = tname + Family.SEP + attNum;
            Element el = parent.getChild("att" + attNum);
            if (el != null) {
                checkAnswersPreHook(el, payload, attachments.get(attNum - 1), atname);
                checkAnswers(el, attachments.get(attNum - 1), null, atname);
                checkAnswersPostHook(el, payload, attachments.get(attNum - 1), atname);
            }
        }
    }

    protected void checkAnswers(Element el, IBaseDataObject payload, List<IBaseDataObject> attachments, String tname) throws DataConversionException {

        int numAtt = JDOMUtil.getChildIntValue(el, "numAttachments");
        if (numAtt > -1) {
            assertEquals("Number of attachments in " + tname, numAtt, attachments != null ? attachments.size() : 0);
        }

        for (Element currentForm : el.getChildren("currentForm")) {
            String cf = currentForm.getTextTrim();
            if (cf != null) {
                Attribute index = currentForm.getAttribute("index");
                if (index != null) {
                    assertEquals(
                            String.format("Current form '%s' not found at position [%d] in %s, %s", cf, index.getIntValue(), tname,
                                    payload.getAllCurrentForms()),
                            payload.currentFormAt(index.getIntValue()), cf);
                } else {
                    assertTrue("Current form " + cf + " not found in " + tname + ", " + payload.getAllCurrentForms(),
                            payload.searchCurrentForm(cf) > -1);
                }
            }
        }

        String cf = el.getChildTextTrim("currentForm");
        if (cf != null) {
            assertTrue(String.format("Current form '%s' not found in %s, %s", cf, tname, payload.getAllCurrentForms()),
                    payload.searchCurrentForm(cf) > -1);
        }

        String ft = el.getChildTextTrim("fileType");
        if (ft != null) {
            assertEquals(String.format("Expected File Type '%s' in %s", ft, tname), ft, payload.getFileType());
        }

        int cfsize = JDOMUtil.getChildIntValue(el, "currentFormSize");
        if (cfsize > -1) {
            assertEquals("Current form size in " + tname, cfsize, payload.currentFormSize());
        }

        String classification = el.getChildTextTrim("classification");
        if (classification != null) {
            assertEquals(String.format("Classification in '%s' is '%s', not expected '%s'", tname, payload.getClassification(), classification),
                    classification, payload.getClassification());
        }

        int dataLength = JDOMUtil.getChildIntValue(el, "dataLength");
        if (dataLength > -1) {
            assertEquals("Data length in " + tname, dataLength, payload.dataLength());
        }

        String shortName = el.getChildTextTrim("shortName");
        if (shortName != null && shortName.length() > 0) {
            assertEquals("Shortname does not match expected in " + tname, shortName, payload.shortName());
        }

        String broke = el.getChildTextTrim("broken");
        if (broke != null && broke.length() > 0) {
            assertEquals("Broken status in " + tname, broke, payload.isBroken() ? "true" : "false");
        }

        // Check specified metadata
        for (Element meta : el.getChildren("meta")) {
            String key = meta.getChildTextTrim("name");
            checkStringValue(meta, payload.getStringParameter(key), tname);
        }

        // Check specified nometa
        for (Element meta : el.getChildren("nometa")) {
            String key = meta.getChildTextTrim("name");
            assertTrue(
                    String.format("Metadata element '%s' in '%s' should not exist, but has value of '%s'", key, tname,
                            payload.getStringParameter(key)),
                    !payload.hasParameter(key));
        }

        // Check the primary view. Even though there is only one
        // primary view there can be multiple elements to test it
        // with differing matchMode operators
        for (Element dataEl : el.getChildren("data")) {
            byte[] payloadData = payload.data();
            checkStringValue(dataEl, new String(payloadData), tname);
        }

        // Check each alternate view
        for (Element view : el.getChildren("view")) {
            String viewName = view.getChildTextTrim("name");
            String lengthStr = view.getChildTextTrim("length");
            byte viewData[] = payload.getAlternateView(viewName);
            assertTrue(String.format("Alternate View '%s' is missing in %s", viewName, tname), viewData != null);
            if (lengthStr != null) {
                assertEquals(String.format("Length of Alternate View '%s' is wrong in %s", viewName, tname), Integer.parseInt(lengthStr),
                        viewData.length);
            }
            checkStringValue(view, new String(viewData), tname);
        }

        // Check for noview items
        for (Element view : el.getChildren("noview")) {
            String viewName = view.getChildTextTrim("name");
            byte viewData[] = payload.getAlternateView(viewName);
            assertTrue(String.format("Alternate View '%s' is present, but should not be, in %s", viewName, tname), viewData == null);
        }


        // Check each extract
        String extractCountStr = el.getChildTextTrim("extractCount");

        if (payload.hasExtractedRecords()) {
            List<IBaseDataObject> extractedChildren = payload.getExtractedRecords();
            int foundCount = extractedChildren.size();

            if (extractCountStr != null) {
                assertEquals(String.format("Number of extracted children in '%s' is %s, not expected %s", tname, foundCount, extractCountStr),
                        Integer.parseInt(extractCountStr), foundCount);
            }

            int attNum = 1;
            for (IBaseDataObject extractedChild : extractedChildren) {
                Element extel = el.getChild("extract" + attNum);
                if (extel != null) {
                    checkAnswers(extel, extractedChild, NO_ATTACHMENTS, String.format("%s::extract%d", tname, attNum));
                }
                attNum++;
            }
        } else {
            if (extractCountStr != null) {
                assertEquals(String.format("No extracted children in '%s' when expecting %s", tname, extractCountStr),
                        Integer.parseInt(extractCountStr), 0);
            }
        }
    }

    protected void checkStringValue(Element meta, String data, String tname) {
        String key = meta.getChildTextTrim("name");
        String value = meta.getChildText("value");
        String matchMode = "equals";
        Attribute mm = meta.getAttribute("matchMode");

        if (value == null) {
            return; // checking the value is optional
        }

        if (mm != null) {
            matchMode = mm.getValue();
        }

        if (matchMode.equals("equals")) {
            assertEquals(meta.getName() + " element '" + key + "' problem in " + tname + " value '" + data + "' does not equal '" + value + "'",
                    value, data);
        } else if (matchMode.equals("index")) {
            assertTrue(meta.getName() + " element '" + key + "' problem in " + tname + " value '" + data + "' does not index '" + value + "'",
                    data.indexOf(value) > -1);
        } else if (matchMode.equals("match")) {
            assertTrue(meta.getName() + " element '" + key + "' problem in " + tname + " value '" + data + "' does not match '" + value + "'",
                    data.matches(value));
        } else if (matchMode.equals("base64")) {
            // decode value as a base64 encoded byte[] array and use the string
            // representation of the byte array for comparison to the incoming value
            value = new String(DatatypeConverter.parseBase64Binary(value));
            assertEquals(meta.getName() + " element '" + key + "' problem in " + tname + " value '" + data + "' does not match '" + value + "'",
                    value, data);
        } else if ("collection".equalsIgnoreCase(matchMode)) {
            Attribute separatorAttribute = meta.getAttribute("collectionSeparator");
            String separator = null != separatorAttribute ? separatorAttribute.getValue() : ","; // comma is default
            // separator
            Collection<String> expectedValues = Arrays.asList(value.split(separator));
            Collection<String> actualValues = Arrays.asList(data.split(separator));
            assertTrue(meta.getName() + " element '" + key + "' problem in " + tname + " did not have equal collection, value ' " + data
                    + "' does not equal '" + value + "' split by separator '" + separator + "'",
                    CollectionUtils.isEqualCollection(expectedValues, actualValues));

        } else {
            fail("Problematic matchMode specified for test '" + matchMode + "' on " + key + " in element " + meta.getName());
        }
    }

    protected void setupPayload(IBaseDataObject payload, Document doc) {
        kff.hash(payload);
        Element root = doc.getRootElement();
        Element setup = root.getChild("setup");
        boolean didSetFiletype = false;
        if (setup != null) {
            List<Element> cfChildren = setup.getChildren("initialForm");
            if (cfChildren.size() > 0) {
                payload.popCurrentForm(); // remove default
            }
            for (Element cf : cfChildren) {
                payload.enqueueCurrentForm(cf.getTextTrim());
            }

            final String classification = setup.getChildTextTrim("classification");
            if (StringUtils.isNotBlank(classification)) {
                payload.setClassification(classification);
            }

            for (Element meta : setup.getChildren("meta")) {
                String key = meta.getChildTextTrim("name");
                String value = meta.getChildTextTrim("value");
                payload.appendParameter(key, value);
            }

            for (Element altView : setup.getChildren("altView")) {
                String name = altView.getChildTextTrim("name");
                byte[] value = altView.getChildText("value").getBytes(StandardCharsets.UTF_8);
                payload.addAlternateView(name, value);
            }

            final String fileType = setup.getChildTextTrim("fileType");
            if (StringUtils.isNotBlank(fileType)) {
                payload.setFileType(fileType);
                didSetFiletype = true;
            }

            final String inputAlternateView = setup.getChildTextTrim("inputAlternateView");
            if (StringUtils.isNotBlank(inputAlternateView)) {
                final byte[] data = payload.data();
                payload.addAlternateView(inputAlternateView, data);
                payload.setData(INCORRECT_VIEW_MESSAGE);
            }

            final String badAlternateView = setup.getChildTextTrim("badAlternateView");
            if (StringUtils.isNotBlank(badAlternateView)) {
                payload.addAlternateView(badAlternateView, INCORRECT_VIEW_MESSAGE);
            }
        }
        if (!didSetFiletype) {
            payload.setFileType(payload.currentForm());
        }
    }
}
