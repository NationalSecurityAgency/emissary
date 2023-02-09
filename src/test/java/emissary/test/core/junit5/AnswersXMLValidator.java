package emissary.test.core.junit5;

import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class AnswersXMLValidator {
    private static final Logger logger = LoggerFactory.getLogger(AnswersXMLValidator.class);
    private static final List<String> validFirstLevelXMLFields = Arrays.asList("setup", "answers");
    private static final List<String> validSetupFields = Arrays.asList("classification", "fileType", "initialForm", "meta");
    private static final List<String> validAnswersFields =
            Arrays.asList("att", "altView", "classification", "currentForm", "currentFormSize", "data", "dataLength",
                    "extract", "extractCount", "fileType", "meta", "nometa", "noview", "numAttachments", "shortName", "view");
    private static final List<String> validInnerInnerFields = Arrays.asList("name", "value");
    private static final List<String> validExtractFields =
            Arrays.asList("currentForm", "currentFormSize", "data", "dataLength", "fileType", "shortName", "meta");
    private static final List<String> validAttFields =
            Arrays.asList("currentForm", "currentFormSize", "data", "dataLength", "fileType", "shortName", "meta");
    private static final List<String> validDataAttributes = Arrays.asList("matchMode");

    public static void validate(Document answerDoc) {
        Element root = answerDoc.getRootElement();
        warnIfNotTrue(root.getName().equals("result"), "Root is '" + root.getName() + "', expected 'result'.");
        List<Element> firstLevelElements = root.getChildren();
        firstLevelElements.forEach(child -> {
            String childName = child.getName();
            warnIfNotTrue(validFirstLevelXMLFields.contains(childName),
                    "'" + childName + "' is not in list of valid first level XML fields:\n" + validFirstLevelXMLFields);
            switch (childName) {
                case "answers":
                    validateInnerChild(child, childName, validAnswersFields);
                    break;
                case "setup":
                    validateInnerChild(child, childName, validSetupFields);
                    break;
                default:
                    warnIfNotTrue(false, "Invalid child '" + childName + "'; this should never happen");
            }
        });
    }

    private static void validateInnerChild(Element child, String childName, List<String> validFields) {
        child.getChildren().forEach(innerChild -> {
            final String innerChildName = innerChild.getName();
            final String innerChildNameTruncated = innerChildName.contains("att") || innerChildName.contains("extract")
                    ? innerChildName.replaceAll("\\d", "")
                    : innerChildName;
            warnIfNotTrue(validFields.contains(innerChildNameTruncated),
                    "'" + innerChildName + "' is not in list of valid '" + childName + "' fields:\n" + validFields);
            List<Element> innerInnerChildren = innerChild.getChildren();
            switch (innerChildNameTruncated) {
                case "data":
                    innerChild.getAttributes().forEach(attribute -> warnIfNotTrue(validDataAttributes.contains(attribute.getName()),
                            "'" + attribute.getName() + "' is not in list of valid data attributes:\n" + validDataAttributes));
                    break;

                case "extract":
                    innerInnerChildren.forEach(innerInnerChild -> {
                        String innerInnerChildName = innerInnerChild.getName();
                        warnIfNotTrue(validExtractFields.contains(innerInnerChildName),
                                "'" + innerInnerChildName + "' is not in list of valid '" + innerChildName + "' fields:\n" + validExtractFields);
                    });
                    break;
                case "att":
                    innerInnerChildren.forEach(innerInnerChild -> {
                        String innerInnerChildName = innerInnerChild.getName();
                        warnIfNotTrue(validAttFields.contains(innerInnerChildName),
                                "'" + innerInnerChildName + "' is not in list of valid '" + innerChildName + "' fields:\n" + validAttFields);
                    });
                    break;
                default:
                    innerInnerChildren.forEach(innerInnerChild -> {
                        String innerInnerChildName = innerInnerChild.getName();
                        warnIfNotTrue(validInnerInnerFields.contains(innerInnerChildName),
                                "'" + innerInnerChildName + "' is not in list of valid '" + innerChildName + "' fields:\n" + validInnerInnerFields);
                    });
            }
        });
    }

    private static void warnIfNotTrue(boolean condition, String message) {
        if (!condition) {
            logger.warn(message);
        }
    }
}
