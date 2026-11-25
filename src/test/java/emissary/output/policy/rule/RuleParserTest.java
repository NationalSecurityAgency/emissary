package emissary.output.policy.rule;

import emissary.core.EmissaryRuntimeException;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.rule.Rule.Dimension;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleParserTest extends UnitTest {

    @Test
    void parsesEachDimension() {
        final Rule payload = RuleParser.parse("deny payload filetype=PDF,EXE form=UNKNOWN outputable=false", 1);
        assertEquals(Outcome.DENY, payload.action());
        assertEquals(Dimension.PAYLOAD, payload.dimension());

        final Rule view = RuleParser.parse("allow view XML.JSON_1_0, JSON_*", 2);
        assertEquals(Outcome.ALLOW, view.action());
        assertEquals(Dimension.VIEW, view.dimension());

        final Rule param = RuleParser.parse("deny param SECRET_*, PASSWORD", 3);
        assertEquals(Dimension.PARAM, param.dimension());

        final Rule value = RuleParser.parse("deny value EMAIL=*@internal, PHONE=555*", 4);
        assertEquals(Dimension.PARAM_VALUE, value.dimension());
    }

    @Test
    void actionAndDimensionAreCaseInsensitive() {
        final Rule rule = RuleParser.parse("DENY View JSON_ML", 1);
        assertEquals(Outcome.DENY, rule.action());
        assertEquals(Dimension.VIEW, rule.dimension());
    }

    @Test
    void starSelectorsMatchEverything() {
        assertEquals(Dimension.VIEW, RuleParser.parse("allow view *", 1).dimension());
        assertEquals(Dimension.PARAM, RuleParser.parse("deny param ALL", 2).dimension());
        assertEquals(Dimension.PAYLOAD, RuleParser.parse("deny payload any", 3).dimension());
    }

    @Test
    void stageLabelUsesIndex() {
        assertEquals("rule #7", RuleParser.parse("deny view JSON_ML", 7).stage());
    }

    @Test
    void rejectsMalformedRules() {
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("allow view", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("maybe deny view JSON_ML", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny table FOO", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny view *.JSON", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny view ", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny payload outputable=maybe", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny payload nope=1", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny value EMAIL", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny param A,,B", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny param A,", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny view JSON,", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny value EMAIL=x,", 1));
        assertThrows(EmissaryRuntimeException.class, () -> RuleParser.parse("deny payload filetype=PDF,", 1));
    }
}
