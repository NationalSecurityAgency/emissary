package emissary.test.core.junit5;

import emissary.config.Configurator;

import org.jdom2.Document;
import org.jdom2.Element;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AnswersXMLValidator {
    private static final List<String> validFirstLevelXMLFields = Arrays.asList("setup", "answers");
    private static final List<String> validSetupFields = Arrays.asList("classification", "fileType", "initialForm", "meta");
    private static final List<String> validAnswersFields =
            Arrays.asList("att", "altView", "classification", "currentForm", "currentFormSize", "data", "dataLength", "extract", "extractCount",
                    "fileType", "meta", "nometa",
                    "noview", "numAttachments", "shortName", "view");
    private static final List<String> validInnerInnerFields = Arrays.asList("name", "value");
    private static final List<String> validDataAttributes = Arrays.asList("matchMode");
    Configurator configG;


    public static void validate(Document answerDoc) {
        Element root = answerDoc.getRootElement();
        assertEquals("result", root.getName());
        List<Element> firstLevelElements = root.getChildren();
        firstLevelElements.forEach(child -> {
            String childName = child.getName();
            assertTrue(validFirstLevelXMLFields.contains(childName),
                    "'" + childName + "' is not in list of valid first level XML fields:\n" + validFirstLevelXMLFields);
            switch (childName) {
                case "answers":
                    validateInnerChild(child, childName, validAnswersFields);
                    break;
                case "setup":
                    validateInnerChild(child, childName, validSetupFields);
                    break;
                default:
                    fail("Invalid child '" + childName + "'; this should never happen");
            }
        });
    }

    private static void validateInnerChild(Element child, String childName, List<String> validFields) {
        child.getChildren().forEach(innerChild -> {
            final String innerChildName = innerChild.getName();
            final String innerChildNameTruncated = Character.isDigit(innerChildName.charAt(innerChildName.length() - 1))
                    ? innerChildName.substring(0, innerChildName.length() - 1)
                    : innerChildName;
            assertTrue(validFields.contains(innerChildNameTruncated),
                    "'" + innerChildName + "' is not in list of valid '" + childName + "' fields:\n" + validFields);
            switch (innerChildName) {
                case "data":
                    innerChild.getAttributes().forEach(attribute -> assertTrue(validDataAttributes.contains(attribute.getName()),
                            "'" + attribute.getName() + "' is not in list of valid data attributes:\n" + validDataAttributes));
                default:
                    List<Element> innerInnerChildren = innerChild.getChildren();
                    innerInnerChildren.forEach(innerInnerChild -> {
                        String innerInnerChildName = innerInnerChild.getName();
                        assertTrue(validInnerInnerFields.contains(innerInnerChildName),
                                "'" + innerInnerChildName + "' is not in list of valid '" + innerChildName + "' fields:\n" + validInnerInnerFields);
                    });
            }
        });
    }
}
