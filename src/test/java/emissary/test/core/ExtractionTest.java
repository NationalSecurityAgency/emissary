package emissary.test.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.kff.KffDataObjectHandler;
import emissary.output.filter.DataFilter;
import emissary.output.filter.JsonOutputFilter;
import emissary.output.filter.XmlOutputFilter;
import emissary.place.IServiceProviderPlace;
import emissary.util.io.ResourceReader;
import emissary.util.xml.JDOMUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

    protected ArrayList<String> filterList = new ArrayList<>();

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

        // Check for Filter to validate against and see if it exists
        String filters = el.getChildTextTrim("filters");
        if (filters != null && filters.length() > 0) {
            String[] filterList;
            String filterName;
            if (filters.contains(",")) {
                filterList = filters.split(",");
                for (String currentFilter : filterList) {
                    filterName = currentFilter.replace(" ", "");
                    assertTrue("Filter " + filterName + " could not be found in emissary/output/filter or burrito/output/filters. " +
                            "Verify filter exists and name is entered correctly in .xml.", findFilter(filterName));
                }
            } else {
                filterName = filters;
                assertTrue("Filter " + filterName + " could not be found in emissary/output/filter or burrito/output/filters. " +
                        "Verify filter exists and name is entered correctly in .xml.", findFilter(filterName));

            }
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
        String validateField = "false";
        Attribute mm = meta.getAttribute("matchMode");
        Attribute vf = meta.getAttribute("validateField");

        if (vf != null) {
            validateField = vf.getValue();
        }

        // meta validateField must be set to true to validate against LogFilter
        // this is currently set to false unless explicitly set to true in .xml
        // see method validateFieldInLogFilter() below for more info
        if (validateField.equals("true")) {
            validateFieldInFilter(key, tname);
        }

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

    /**
     * Method to validate a meta key name against LogFilter.cfg fields/parameters. Currently, this is set to NOT RUN unless
     * the meta field specifies it in the .xml file like:
     *
     * meta validateField="true"
     *
     * *IF RUNNING THROUGH BURRITO* Right now, it is only set to be able to find DataFilter, JsonOutputFilter, and
     * XmlOutputFilter when looking for Emissary filters. If more filers are added, they will need to be added here and in
     * findFilter(). This is due to the import of the class and the getResourceAsStream() necessary to grab Emissary filters
     * in Burrito.
     *
     * @param key the current meta key name to validate against LogFilter
     * @param tname the current test name
     */
    protected void validateFieldInFilter(String key, String tname) {
        String resourceName;

        // config properties to check
        String blacklistField = "BLACKLIST_FIELD";
        String extraParam = "EXTRA_PARAM";
        String extraTldParam = "EXTRA_TLD_PARAM";
        // config prefix properties to check
        String extraPrefix = "EXTRA_PREFIX";
        String extraTldPrefix = "EXTRA_TLD_PREFIX";
        // prefix initialization
        String prefix;

        int lineCount = 1;

        // check all files in filterList to validate field against
        for (int i = 0; i < filterList.size(); i++) {
            try {
                resourceName = filterList.get(i);
                // initialize variables
                File filterFile;
                InputStream emissaryStream = null;
                InputStreamReader isr = null;
                BufferedReader reader;
                String fileName;
                // Check to see if resourceName includes "InputStream"
                if (!resourceName.contains("InputStream")) {
                    // set resource to File and set BufferedReader to read this File
                    filterFile = new File(resourceName);
                    reader = new BufferedReader(new FileReader(filterFile));
                } else {
                    // set resource to matching class and filter stream, and set BufferedReader to read this InputStream
                    resourceName = resourceName.replace("InputStream", "");
                    if (resourceName.contains("DataFilter")) {
                        emissaryStream = DataFilter.class.getResourceAsStream("DataFilter.cfg");
                    } else if (resourceName.contains("JsonOutputFilter")) {
                        emissaryStream = JsonOutputFilter.class.getResourceAsStream("JsonOutputFilter.cfg");
                    } else if (resourceName.contains("XmlOutputFilter")) {
                        emissaryStream = XmlOutputFilter.class.getResourceAsStream("XmlOutputFilter.cfg");
                    } else {
                        logger.warn("No InputStream will be found for " + resourceName + ". Make sure the Class and Resource are referenced in" +
                                " ExtractionTest within validateFieldInFilter.");
                    }
                    assert emissaryStream != null;
                    isr = new InputStreamReader(emissaryStream);
                    reader = new BufferedReader(isr);
                }
                // Gets simplified fileName
                fileName = Paths.get(resourceName).getFileName().toString();

                // Run through current file with reader
                while (reader.ready()) {
                    String currentLine = reader.readLine();
                    // if line is commented out, go to next line without check
                    if (!currentLine.startsWith("#")) {
                        // check if EXTRA_PARAM = "*" is defined
                        // if yes, pass validation
                        if (currentLine.equals("EXTRA_PARAM = \"*\"")) {
                            logger.info("In " + fileName + ": EXTRA_PARAM = \"*\" on line " + lineCount + " validates \"" + key + "\".");
                            reader.close();
                            return;
                        }
                        // check if BLACKLIST_FIELD, EXTRA_PARAM, or EXTRA_TLD_PARAM = passed key (meta name)
                        else if (currentLine.contains(blacklistField) || currentLine.contains(extraParam) || currentLine.contains(extraTldParam)) {
                            if (currentLine.contains(key)) {
                                logger.info(
                                        "In " + fileName + ": Parameter \"" + key + "\" found on line " + lineCount + ". \"" + key
                                                + "\" is validated.");
                                reader.close();
                                return;
                            }
                        }
                        // check if a prefix is defined in either EXTRA_PREFIX or EXTRA_TLD_PREFIX
                        // if prefix is defined and key has matching prefix, pass validation
                        else if (currentLine.contains(extraPrefix) || currentLine.contains(extraTldPrefix)) {
                            // cut string down to be just the prefix in quotations
                            int preStart = currentLine.indexOf("\"") + 1;
                            int preEnd = currentLine.length() - 1;
                            prefix = currentLine.substring(preStart, preEnd);
                            if (key.startsWith(prefix)) {
                                logger.info("In " + fileName + ": Parameter Prefix \"" + prefix + "\" found on line " + lineCount + ". \"" + key
                                        + "\" is validated.");
                                reader.close();
                                return;
                            }
                        }
                    }
                    lineCount++;
                }
                reader.close();
                emissaryStream.close();
                isr.close();
                if ((i + 1) == filterList.size()) {
                    logger.warn(key + " not found in " + fileName + ".");
                } else {
                    logger.warn(key + " not found in " + fileName + ". Moving to next file.");
                }
            } catch (Exception e) {
                if ((i + 1) == filterList.size()) {
                    logger.warn("Error while validating " + key + ": " + e);
                } else {
                    logger.warn("Error while validating " + key + ": " + e + ". Moving to next file.");
                }
            }
        }
        // if loops through all files and cannot find matching field/param for key, then validation fails
        if (filterList.size() == 0) {
            fail("No filters were passed to validate against from " + tname);
        } else {
            // Remove all added path info, just get filter name
            ArrayList<String> simpleFilterList = new ArrayList<>();
            for (String s : filterList) {
                String cFilter = Paths.get(s).getFileName().toString().replace("InputStream", "");
                simpleFilterList.add(cFilter);
            }
            fail(tname + " - Field \"" + key + "\" not found in Filter files: " + simpleFilterList);
        }

    }

    /**
     * Method that searches to see if filter exists in Emissary or Burrito filter folders.
     * Validates filter exists, then adds it to filterList.
     * If filter does not exist, returns false
     * This causes an Assertion error as it should return true. Tells user to check filter in .xml.
     *
     * @param filterName
     * @return
     */
    protected boolean findFilter(String filterName) {
        boolean result = false;

        String filter;
        if (filterName.endsWith(".cfg")) {
            filter = filterName;
        } else {
            filter = filterName + ".cfg";
        }

        // Base file paths for filters
        File emissaryFilterFolder = new File("./src/main/resources/emissary/output/filter");
        File burritoFilterFolder = new File("./src/main/resources/com/burrito/output/filters");

        // These are the blacklist and whitelist .cfg. Not sure if needed here, but added in case
        // String burritoTestFilters = "./core/src/test/resources/com/burrito/output/filters";

        String filterPath;

        // Check if emissaryFilterFolder exists, this will only be the case when run through Emissary, and will only accept
        // Emissary filters
        if (emissaryFilterFolder.exists() && (emissaryFilterFolder.list() != null)) {
            String[] emissaryFilterList = emissaryFilterFolder.list();
            for (String currentFile : emissaryFilterList) {
                if (currentFile.equals(filter)) {
                    filterPath = emissaryFilterFolder.getPath() + "/" + filter;
                    filterList.add(filterPath);
                    logger.info(filter + " exists in burrito/output/filters. Filter added to list to validate field against.");
                    result = true;
                }
            }
        }
        // Check if burritoFilterFolder exists, this will only be the case when run through Burrito
        else if (burritoFilterFolder.exists() && (burritoFilterFolder.list() != null)) {
            String[] burritoFilterList = burritoFilterFolder.list();
            // If filter passed through .xml is one of the Emissary existing filters, this is where it checks this
            if (filter.equals("JsonOutputFilter.cfg") || filter.equals("DataFilter.cfg") || filter.equals("XmlOutputFilter.cfg")) {
                // Set filterPath to be different from the Emissary path to specifiy that the InputStream is necessary
                filterPath = "InputStream" + filter;
                filterList.add(filterPath);
                logger.info(filter + " will be read through InputStream (necessary in Burrito). Filter added to list to validate field against.");
                result = true;
            }
            // This checks against explicitly Burrito filters
            else {
                for (String currentFile : burritoFilterList) {
                    if (currentFile.equals(filter)) {
                        filterPath = burritoFilterFolder.getPath() + "/" + filter;
                        filterList.add(filterPath);
                        logger.info(filter + " exists in burrito/output/filters. Filter added to list to validate field against.");
                        result = true;
                    }
                }
            }
        }

        if (!result)
            logger.info(filter
                    + " could not be found in emissary/output/filter or burrito/output/filters. Verify filter exists and name is entered correctly in .xml.");
        return result;
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
