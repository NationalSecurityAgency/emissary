package emissary.test.core.junit5;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.kff.KffDataObjectHandler;
import emissary.place.IServiceProviderPlace;
import emissary.util.io.ResourceReader;
import emissary.util.xml.JDOMUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Attribute;
import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.bind.DatatypeConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class ExtractionTest extends UnitTest {

    protected static Logger logger = LoggerFactory.getLogger(ExtractionTest.class);

    private static final List<IBaseDataObject> NO_ATTACHMENTS = Collections.emptyList();
    private static final byte[] INCORRECT_VIEW_MESSAGE = "This is the incorrect view, the place should not have processed this view".getBytes();

    protected KffDataObjectHandler kff =
            new KffDataObjectHandler(KffDataObjectHandler.TRUNCATE_KNOWN_DATA, KffDataObjectHandler.SET_FORM_WHEN_KNOWN,
                    KffDataObjectHandler.SET_FILE_TYPE);
    protected IServiceProviderPlace place = null;

    @BeforeEach
    public void setUpPlace() throws Exception {
        place = createPlace();
    }

    @AfterEach
    public void tearDownPlace() {
        if (place != null) {
            place.shutDown();
            place = null;
        }
    }

    /**
     * Derived classes must implement this
     */
    public abstract IServiceProviderPlace createPlace() throws IOException;

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(ExtractionTest.class);
    }

    protected String getInitialForm(final String resource) {
        return resource.replaceAll("^.*/([^/@]+)(@\\d+)?\\.dat$", "$1");
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testExtractionPlace(String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        // Need a pair consisting of a .dat file and a .xml file (answers)
        Document controlDoc = getAnswerDocumentFor(resource);
        if (controlDoc == null) {
            fail("No answers provided for test " + resource);
        }

        try (InputStream doc = new ResourceReader().getResourceAsStream(resource)) {
            byte[] data = IOUtils.toByteArray(doc);
            String initialForm = getInitialForm(resource);
            IBaseDataObject payload = DataObjectFactory.getInstance(data, resource, initialForm);
            setupPayload(payload, controlDoc);
            processPreHook(payload, controlDoc);
            List<IBaseDataObject> attachments = place.agentProcessHeavyDuty(payload);
            processPostHook(payload, attachments);
            checkAnswersPreHook(controlDoc, payload, attachments, resource);
            checkAnswers(controlDoc, payload, attachments, resource);
            checkAnswersPostHook(controlDoc, payload, attachments, resource);
        } catch (Exception ex) {
            logger.error("Error running test {}", resource, ex);
            fail("Cannot run test " + resource, ex);
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
            assertEquals(numAtt, attachments != null ? attachments.size() : 0, "Number of attachments in " + tname);
        }

        for (Element currentForm : el.getChildren("currentForm")) {
            String cf = currentForm.getTextTrim();
            if (cf != null) {
                Attribute index = currentForm.getAttribute("index");
                if (index != null) {
                    assertEquals(payload.currentFormAt(index.getIntValue()), cf,
                            String.format("Current form '%s' not found at position [%d] in %s, %s", cf, index.getIntValue(), tname,
                                    payload.getAllCurrentForms()));
                } else {
                    assertTrue(payload.searchCurrentForm(cf) > -1,
                            String.format("Current form %s not found in %s, %s", cf, tname, payload.getAllCurrentForms()));
                }
            }
        }

        String cf = el.getChildTextTrim("currentForm");
        if (cf != null) {
            assertTrue(payload.searchCurrentForm(cf) > -1,
                    String.format("Current form '%s' not found in %s, %s", cf, tname, payload.getAllCurrentForms()));
        }

        String ft = el.getChildTextTrim("fileType");
        if (ft != null) {
            assertEquals(ft, payload.getFileType(), String.format("Expected File Type '%s' in %s", ft, tname));
        }

        int cfsize = JDOMUtil.getChildIntValue(el, "currentFormSize");
        if (cfsize > -1) {
            assertEquals(cfsize, payload.currentFormSize(), "Current form size in " + tname);
        }

        String classification = el.getChildTextTrim("classification");
        if (classification != null) {
            assertEquals(classification, payload.getClassification(),
                    String.format("Classification in '%s' is '%s', not expected '%s'", tname, payload.getClassification(), classification));
        }

        int dataLength = JDOMUtil.getChildIntValue(el, "dataLength");
        if (dataLength > -1) {
            assertEquals(dataLength, payload.dataLength(), "Data length in " + tname);
        }

        String shortName = el.getChildTextTrim("shortName");
        if (shortName != null && shortName.length() > 0) {
            assertEquals(shortName, payload.shortName(), "Shortname does not match expected in " + tname);
        }

        String broke = el.getChildTextTrim("broken");
        if (broke != null && broke.length() > 0) {
            assertEquals(broke, payload.isBroken() ? "true" : "false", "Broken status in " + tname);
        }

        // Check specified metadata
        for (Element meta : el.getChildren("meta")) {
            String key = meta.getChildTextTrim("name");
            checkStringValue(meta, payload.getStringParameter(key), tname);
        }

        // Check specified nometa
        for (Element meta : el.getChildren("nometa")) {
            String key = meta.getChildTextTrim("name");
            assertFalse(payload.hasParameter(key),
                    String.format("Metadata element '%s' in '%s' should not exist, but has value of '%s'", key, tname,
                            payload.getStringParameter(key)));
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
            byte[] viewData = payload.getAlternateView(viewName);
            assertNotNull(viewData, String.format("Alternate View '%s' is missing in %s", viewName, tname));
            if (lengthStr != null) {
                assertEquals(Integer.parseInt(lengthStr), viewData.length,
                        String.format("Length of Alternate View '%s' is wrong in %s", viewName, tname));
            }
            checkStringValue(view, new String(viewData), tname);
        }

        // Check for noview items
        for (Element view : el.getChildren("noview")) {
            String viewName = view.getChildTextTrim("name");
            byte[] viewData = payload.getAlternateView(viewName);
            assertNull(viewData, String.format("Alternate View '%s' is present, but should not be, in %s", viewName, tname));
        }


        // Check each extract
        String extractCountStr = el.getChildTextTrim("extractCount");

        if (payload.hasExtractedRecords()) {
            List<IBaseDataObject> extractedChildren = payload.getExtractedRecords();
            int foundCount = extractedChildren.size();

            if (extractCountStr != null) {
                assertEquals(Integer.parseInt(extractCountStr), foundCount,
                        String.format("Number of extracted children in '%s' is %s, not expected %s", tname, foundCount, extractCountStr));
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
                assertEquals(0, Integer.parseInt(extractCountStr),
                        String.format("No extracted children in '%s' when expecting %s", tname, extractCountStr));
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
            assertEquals(value, data,
                    String.format("%s element '%s' problem in %s value '%s' does not equal '%s'", meta.getName(), key, tname, data, value));
        } else if (matchMode.equals("index") || matchMode.equals("contains")) {
            assertTrue(data.contains(value),
                    String.format("%s element '%s' problem in %s value '%s' does not index '%s'", meta.getName(), key, tname, data, value));
        } else if (matchMode.equals("!index") || matchMode.equals("!contains")) {
            assertFalse(data.contains(value),
                    String.format("%s element '%s' problem in %s value '%s' should not be indexed in '%s'", meta.getName(), key, tname, value, data));
        } else if (matchMode.equals("match")) {
            assertTrue(data.matches(value),
                    String.format("%s element '%s' problem in %s value '%s' does not match '%s'", meta.getName(), key, tname, data, value));
        } else if (matchMode.equals("base64")) {
            // decode value as a base64 encoded byte[] array and use the string
            // representation of the byte array for comparison to the incoming value
            value = new String(DatatypeConverter.parseBase64Binary(value));
            assertEquals(value, data,
                    String.format("%s element '%s' problem in %s value '%s' does not match '%s'", meta.getName(), key, tname, data, value));
        } else if ("collection".equalsIgnoreCase(matchMode)) {
            Attribute separatorAttribute = meta.getAttribute("collectionSeparator");
            String separator = null != separatorAttribute ? separatorAttribute.getValue() : ","; // comma is default
            // separator
            Collection<String> expectedValues = Arrays.asList(value.split(separator));
            Collection<String> actualValues = Arrays.asList(data.split(separator));
            assertTrue(CollectionUtils.isEqualCollection(expectedValues, actualValues),
                    String.format(
                            "%s element '%s' problem in %s did not have equal collection, value '%s' does not equal '%s' split by separator '%s'",
                            meta.getName(), key, tname, data, value, separator));

        } else {
            fail(String.format("Problematic matchMode specified for test '%s' on %s in element %s", matchMode, key, meta.getName()));
        }
    }

    protected void setupPayload(IBaseDataObject payload, Document doc) {
        kff.hash(payload);
        Element root = doc.getRootElement();
        Element setup = root.getChild("setup");
        boolean didSetFiletype = false;
        if (setup != null) {
            List<Element> cfChildren = setup.getChildren("initialForm");
            if (!cfChildren.isEmpty()) {
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
