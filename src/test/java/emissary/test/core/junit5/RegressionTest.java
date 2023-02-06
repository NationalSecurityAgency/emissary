package emissary.test.core.junit5;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlHelper;

import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <p>
 * This test acts similarly to ExtractionTest; however, it compares the entire BDO instead of just what is defined in
 * the XML. In other words, the XML must define exactly the output of the Place, no more and no less. There are methods
 * provided to generate the XML required.
 * </p>
 * 
 * <p>
 * To implement this for a test, you should:
 * </p>
 * <ol>
 * <li>Extend this class</li>
 * <li>Override {@link #generateAnswers()} to return true, which will generate the XML answer files</li>
 * <li>Optionally, override {@link #getInitialIbdo(String)} and {@link #modifyFinalIbdo(String, IBaseDataObject)} if you
 * want to customise the behaviour of providing the initial IBDO, such as overriding the current form before/after
 * processing</li>
 * <li>Run the tests, which should pass - if they don't, you either have incorrect processing which needs fixing, or you
 * need to further customise the initial/final IBDOs.</li>
 * <li>Once the tests pass, you can remove the overridden method(s) added above.</li>
 * </ol>
 */
public abstract class RegressionTest extends ExtractionTest {

    // XML builder to read XML answer file in
    private static final SAXBuilder xmlBuilder = new SAXBuilder(XMLReaders.NONVALIDATING);

    /**
     * Override this to generate XML for data files
     * 
     * Return the initial form for the provided dat files. If multiple types are to be tested,
     * 
     * @return defaults to false if no XML should be generated (i.e. normal case of executing tests) or true to generate
     *         automatically
     */
    protected boolean generateAnswers() {
        return false;
    }

    /**
     * Allow the initial IBDO to be overridden - for example, adding additional previous forms
     * 
     * This is used in the simple case to generate an IBDO from the file on disk and override the filename
     * 
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    protected IBaseDataObject getInitialIbdo(final String resource) {
        return RegressionTestUtil.getInitialIbdoWithFormInFilename(resource, kff);
    }

    /**
     * Allow the generated IBDO to be overridden - for example, adding certain field values. Will modify the provided IBDO.
     * 
     * This is used in the simple case to set the current form for the final object to be taken from the file name. If the
     * test worked correctly no change will be made, but if there is a discrepancy this will be highlighted afterwards when
     * the diff takes place.
     * 
     * @param resource path to the dat file
     * @param finalIbdo the existing final BDO after it's been processed by a place
     */
    protected void modifyFinalIbdo(final String resource, final IBaseDataObject finalIbdo) {
        RegressionTestUtil.modifyFinalIbdoWithFormInFilename(resource, finalIbdo);
    }

    @Override
    protected String getInitialForm(String resource) {
        return RegressionTestUtil.getInitialFormFromFilename(resource);
    }

    @ParameterizedTest
    @MethodSource("data")
    @Override
    public void testExtractionPlace(final String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        if (generateAnswers()) {
            try {
                generateAnswerFiles(resource);
            } catch (final Exception e) {
                fail("Unable to generate answer files", e);
            }
        }

        // Run the normal extraction/regression tests
        super.testExtractionPlace(resource);
    }

    /**
     * Actually generate the answer file for a given resource
     * 
     * Takes initial form & final forms from the filename
     * 
     * @param resource to generate against
     * @throws Exception if an error occurs during processing
     */
    private void generateAnswerFiles(final String resource) throws Exception {
        // Get the data and create a channel factory to it
        final IBaseDataObject initialIbdo = getInitialIbdo(resource);

        // Clone the BDO to create an 'after' copy
        final IBaseDataObject finalIbdo = IBaseDataObjectHelper.clone(initialIbdo, true);
        // Actually process the BDO and keep the children
        final List<IBaseDataObject> results = place.agentProcessHeavyDuty(finalIbdo);

        // Allow overriding final IBDO before serialising to XML
        modifyFinalIbdo(resource, finalIbdo);

        // Generate the full XML (setup & answers from before & after)
        final String xmlContent = IBaseDataObjectXmlHelper.xmlFromIbdo(finalIbdo, results, initialIbdo);
        // Write out the XML to disk
        RegressionTestUtil.writeXml(resource, xmlContent);
    }

    /**
     * When generating XML answer files, we need to use the src version rather than target
     * 
     * This method returns the XML file from that location.
     * 
     * @return the XML file in the src directory (not target)
     */
    @Override
    protected Document getAnswerDocumentFor(final String resource) {
        // Short circuit to getting the answers document the regular way
        if (!generateAnswers()) {
            return super.getAnswerDocumentFor(resource);
        }

        try {
            return xmlBuilder.build(RegressionTestUtil.getXmlPath(resource).toFile());
        } catch (final JDOMException | IOException e) {
            logger.debug(String.format("No valid answer document provided for %s", resource), e);
            return null;
        }
    }

    @Override
    protected void setupPayload(final IBaseDataObject payload, final Document answers) {
        RegressionTestUtil.setupPayload(payload, answers);
    }

    @Override
    protected void checkAnswers(final Document answers, final IBaseDataObject payload,
            final List<IBaseDataObject> attachments, final String tname) throws DataConversionException {
        RegressionTestUtil.checkAnswers(answers, payload, attachments, tname, place.getClass().getName());
    }

}
