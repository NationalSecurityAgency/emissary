package emissary.output.policy.rule;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.request.ParamRequest;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleStageTest extends UnitTest {

    private static RuleStage stage(final String rule) {
        return new RuleStage(Collections.singletonList(RuleParser.parse(rule, 1)));
    }

    private static IBaseDataObject payload(final String fileType) {
        return DataObjectFactory.getInstance(new byte[0], "test", fileType);
    }

    @Test
    void payloadFileTypeMatches() {
        final RuleStage stage = stage("deny payload filetype=PDF,EXE");

        assertTrue(stage.decide(PayloadRequest.from(payload("PDF"))).isDenied());
        assertTrue(stage.decide(PayloadRequest.from(payload("EXE"))).isDenied());
        assertEquals(Outcome.ABSTAIN, stage.decide(PayloadRequest.from(payload("XML"))).outcome());
    }

    @Test
    void payloadFileTypePrefixMatches() {
        final RuleStage stage = stage("deny payload filetype=MS*");

        assertTrue(stage.decide(PayloadRequest.from(payload("MSWORD"))).isDenied());
        assertEquals(Outcome.ABSTAIN, stage.decide(PayloadRequest.from(payload("XML"))).outcome());
    }

    @Test
    void payloadFormAndOutputableMustAllMatch() {
        final RuleStage stage = stage("deny payload filetype=PDF outputable=false");
        final IBaseDataObject pdf = payload("PDF");
        pdf.setOutputable(false);

        assertTrue(stage.decide(PayloadRequest.from(pdf)).isDenied());

        final IBaseDataObject outputablePdf = payload("PDF");
        assertEquals(Outcome.ABSTAIN, stage.decide(PayloadRequest.from(outputablePdf)).outcome());

        final IBaseDataObject hiddenXml = payload("XML");
        hiddenXml.setOutputable(false);
        assertEquals(Outcome.ABSTAIN, stage.decide(PayloadRequest.from(hiddenXml)).outcome());
    }

    @Test
    void viewMatchesExactQualifiedAndWildcard() {
        final PayloadRequest request = PayloadRequest.from(payload("XML"));

        assertTrue(stage("deny view JSON_ML").decide(new ViewRequest(request, "JSON_ML")).isDenied());
        assertTrue(stage("deny view XML.JSON_1_0").decide(new ViewRequest(request, "JSON_1_0")).isDenied());
        assertTrue(stage("deny view JSON_*").decide(new ViewRequest(request, "JSON_TEXT")).isDenied());
        assertTrue(stage("deny view *").decide(new ViewRequest(request, "WHATEVER")).isDenied());

        assertEquals(Outcome.ABSTAIN,
                stage("deny view JSON_ML").decide(new ViewRequest(request, "JSON_TEXT")).outcome());
    }

    @Test
    void fileTypeQualifiedViewDoesNotMatchOtherFileTypes() {
        final RuleStage stage = stage("deny view PDF.JSON_1_0");

        assertTrue(stage.decide(new ViewRequest(PayloadRequest.from(payload("PDF")), "JSON_1_0")).isDenied());
        assertEquals(Outcome.ABSTAIN,
                stage.decide(new ViewRequest(PayloadRequest.from(payload("XML")), "JSON_1_0")).outcome());
    }

    @Test
    void paramMatchesExactPrefixAndAll() {
        final PayloadRequest request = PayloadRequest.from(payload("XML"));

        assertTrue(stage("deny param PASSWORD").decide(new ParamRequest(request, "PASSWORD", null)).isDenied());
        assertTrue(stage("deny param SECRET_*").decide(new ParamRequest(request, "SECRET_TOKEN", null)).isDenied());
        assertTrue(stage("deny param *").decide(new ParamRequest(request, "ANYTHING", null)).isDenied());

        assertEquals(Outcome.ABSTAIN,
                stage("deny param PASSWORD").decide(new ParamRequest(request, "FOO", null)).outcome());
    }

    @Test
    void valueMatchesKeyAndPattern() {
        final PayloadRequest request = PayloadRequest.from(payload("XML"));

        assertTrue(stage("deny value EMAIL=*@internal").decide(new ParamRequest(request, "EMAIL", "boss@internal")).isDenied());
        assertTrue(stage("deny value EMAIL=*@internal").decide(new ParamRequest(request, "EMAIL", "x@internal")).isDenied());
        assertTrue(stage("deny value PHONE=555*").decide(new ParamRequest(request, "PHONE", "555-1234")).isDenied());
        assertTrue(stage("deny value CODE=A?C").decide(new ParamRequest(request, "CODE", "ABC")).isDenied());

        assertEquals(Outcome.ABSTAIN,
                stage("deny value EMAIL=*@internal").decide(new ParamRequest(request, "EMAIL", "boss@external")).outcome());
        assertEquals(Outcome.ABSTAIN,
                stage("deny value EMAIL=*@internal").decide(new ParamRequest(request, "OTHER", "x@internal")).outcome());
    }

    @Test
    void valueAllAppliesToEveryKey() {
        final PayloadRequest request = PayloadRequest.from(payload("XML"));

        assertTrue(stage("deny value ALL=*@internal").decide(new ParamRequest(request, "EMAIL", "a@internal")).isDenied());
        assertTrue(stage("deny value *=*@internal").decide(new ParamRequest(request, "ANYTHING", "a@internal")).isDenied());
    }

    @Test
    void keyAndValueDecisionsUseTheirOwnRules() {
        final RuleStage stage = new RuleStage(Arrays.asList(
                RuleParser.parse("deny param EMAIL", 1),
                RuleParser.parse("deny value EMAIL=*@internal", 2)));
        final PayloadRequest request = PayloadRequest.from(payload("XML"));

        assertTrue(stage.decide(new ParamRequest(request, "EMAIL", null)).isDenied());
        assertTrue(stage.decide(new ParamRequest(request, "EMAIL", "a@internal")).isDenied());
        assertEquals(Outcome.ABSTAIN,
                stage.decide(new ParamRequest(request, "EMAIL", "a@external")).outcome());
    }
}
