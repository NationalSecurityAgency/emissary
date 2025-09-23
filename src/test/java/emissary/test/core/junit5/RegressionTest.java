package emissary.test.core.junit5;

import emissary.core.DiffCheckConfiguration;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectXmlCodecs;
import emissary.core.IBaseDataObjectXmlHelper;
import emissary.test.core.junit5.LogbackTester.SimplifiedLogEvent;
import emissary.util.PlaceComparisonHelper;
import emissary.util.io.ResourceReader;

import jakarta.annotation.Nullable;
import org.jdom2.Document;
import org.jdom2.Element;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static emissary.core.IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
import static emissary.core.constants.IbdoXmlElementNames.ANSWERS;
import static emissary.core.constants.IbdoXmlElementNames.SETUP;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * <li>Either Override {@link #generateAnswers()} to return true or set the generateAnswers system property to true,
 * which will generate the XML answer files</li>
 * <li>Optionally, to generate answers file without changing code run {@code mvn clean test -DgenerateAnswers=true}</li>
 * <li>Optionally, override the various provided methods if you want to customise the behaviour of providing the IBDO
 * before/after processing</li>
 * <li>Run the tests, which should pass - if they don't, you either have incorrect processing which needs fixing, or you
 * need to further customise the initial/final IBDOs.</li>
 * <li>Once the tests pass, you can remove the overridden method(s) added above.</li>
 * </ol>
 */
public abstract class RegressionTest extends ExtractionTest {

    /* Difference configuration to use when comparing IBDO's. */
    private static final DiffCheckConfiguration DIFF_CHECK = DiffCheckConfiguration.configure().enableData().enableKeyValueParameterDiff().build();

    @Override
    public String getAnswerXsd() {
        return "emissary/test/core/schemas/regression.xsd";
    }

    @Override
    protected AnswerGenerator createAnswerGenerator() {
        return new RegressionTestAnswerGenerator();
    }

    /**
     * This method returns the XML element encoders.
     *
     * @return the XML element encoders.
     */
    @Override
    protected IBaseDataObjectXmlCodecs.ElementEncoders getEncoders() {
        return SHA256_ELEMENT_ENCODERS;
    }

    @Override
    @Nullable
    protected String getInitialForm(final String resource) {
        try {
            final Path datFileUrl = Paths.get(new ResourceReader().getResource(resource).toURI());
            final InitialFinalFormFormat datFile = new InitialFinalFormFormat(datFileUrl);
            return datFile.getInitialForm();
        } catch (final URISyntaxException e) {
            fail("Unable to get initial form from filename", e);
            return null;
        }
    }

    @Override
    protected void setupPayload(final IBaseDataObject payload, final Document answers) {
        final Element root = answers.getRootElement();

        if (root != null) {
            final Element parent = root.getChild(SETUP);

            if (parent != null) {
                payload.popCurrentForm(); // Remove default form put on by ExtractionTest.
                payload.setFileType(null); // Remove default filetype put on by ExtractionTest.
                // The only other fields set are data and filename.

                IBaseDataObjectXmlHelper.ibdoFromXmlMainElements(parent, payload, getDecoders(payload.getFilename()));
            }
        }
    }

    @Override
    protected void checkAnswers(final Document answers, final IBaseDataObject payload,
            final List<IBaseDataObject> attachments, final String tname) {

        final Element root = answers.getRootElement();
        final Element parent = root.getChild(ANSWERS);

        assertNotNull(parent, "No 'answers' section found!");

        final List<IBaseDataObject> expectedAttachments = new ArrayList<>();
        final IBaseDataObject expectedIbdo = IBaseDataObjectXmlHelper.ibdoFromXml(answers, expectedAttachments, getDecoders(tname));
        final String differences = PlaceComparisonHelper.checkDifferences(expectedIbdo, payload, expectedAttachments,
                attachments, place.getClass().getName(), DIFF_CHECK);

        assertNull(differences, generateAnswers() ? differences
                + "\nNOTE: Since 'generateAnswers' is true, these differences could indicate non-deterministic processing in the tested code path\n"
                : differences);

        assertIterableEquals(SimplifiedLogEvent.fromXml(parent), actualSimplifiedLogEvents);
    }
}
