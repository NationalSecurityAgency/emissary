package emissary.test.core.junit5;

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

    @Override
    public boolean isStrict() {
        return true;
    }

}
