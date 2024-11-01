package emissary.transform;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.ExtractionTest;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static emissary.core.Form.PREFIXES_LANG;
import static emissary.core.Form.SUFFIXES_HTMLESC;
import static emissary.core.constants.Parameters.DOCUMENT_TITLE;
import static emissary.core.constants.Parameters.SUMMARY;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Deprecated
public class HtmlEscapePlaceTest extends ExtractionTest {

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(HtmlEscapePlaceTest.class);
    }

    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new HtmlEscapePlace();
    }

    @Nested
    class HtmlEscapePlaceUnitTest extends UnitTest {

        protected static final String EXPECTED = "TESTING";

        HtmlEscapePlace place;
        IBaseDataObject d;

        @Override
        @BeforeEach
        public void setUp() throws Exception {
            super.setUp();
            place = new HtmlEscapePlace();
            d = DataObjectFactory.getInstance("X".getBytes(), "Y", "Z");
        }

        @Test
        void unescapeAltViews() {
            String view = "TEXT-" + EXPECTED;
            d.addAlternateView(view, (EXPECTED + "&#39;").getBytes(StandardCharsets.UTF_8));
            place.unescapeAltViews(d);
            assertEquals(EXPECTED + "'", new String(d.getAlternateView(view)));
        }

        @Test
        void unescapeSummary() {
            d.putParameter(SUMMARY, EXPECTED + "&#39;");
            place.unescapeSummary(d);
            assertEquals(EXPECTED + "'", d.getStringParameter(SUMMARY));
        }

        @Test
        void unescapeDocTitle() {
            d.putParameter(DOCUMENT_TITLE, EXPECTED + "&#39;");
            place.unescapeDocTitle(d);
            assertEquals(EXPECTED + "'", d.getStringParameter(DOCUMENT_TITLE));
        }

        @Test
        void processEncoding() {
            String expected = PREFIXES_LANG + EXPECTED;
            d.setFontEncoding(expected + SUFFIXES_HTMLESC);
            place.processEncoding(d);
            assertEquals(expected, d.getFontEncoding());
        }

        @Test
        void processCurrentForms() {
            String expected = PREFIXES_LANG + EXPECTED;
            d.setCurrentForm(expected + SUFFIXES_HTMLESC);
            place.processCurrentForms(d);
            assertEquals(expected, d.currentForm());
        }

        @Test
        void makeString() {
            String actual = HtmlEscapePlace.makeString(EXPECTED.getBytes(StandardCharsets.UTF_8));
            assertEquals(EXPECTED, actual);
        }
    }

}
