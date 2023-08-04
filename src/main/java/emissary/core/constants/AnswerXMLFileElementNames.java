package emissary.core.constants;

import emissary.core.BaseDataObject;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AnswerXMLFileElementNames {
    // common XML element names
    public static final String ANSWERS_ELEMENT_NAME = "answers";
    public static final String BIRTH_ORDER_ELEMENT_NAME = "birthOrder";
    public static final String BROKEN_ELEMENT_NAME = "broken";
    public static final String CLASSIFICATION_ELEMENT_NAME = "classification";
    public static final String CURRENT_FORM_ELEMENT_NAME = "currentForm";
    public static final String DATA_ELEMENT_NAME = "data";
    public static final String FILENAME_ELEMENT_NAME = "filename";
    public static final String FONT_ENCODING_ELEMENT_NAME = "fontEncoding";
    public static final String FOOTER_ELEMENT_NAME = "footer";
    public static final String HEADER_ELEMENT_NAME = "header";
    public static final String HEADER_ENCODING_ELEMENT_NAME = "headerEncoding";
    public static final String ID_ELEMENT_NAME = "id";
    public static final String NUM_CHILDREN_ELEMENT_NAME = "numChildren";
    public static final String NUM_SIBLINGS_ELEMENT_NAME = "numSiblings";
    public static final String OUTPUTABLE_ELEMENT_NAME = "outputable";
    public static final String PARAMETER_ELEMENT_NAME = "meta";
    public static final String PRIORITY_ELEMENT_NAME = "priority";
    public static final String PROCESSING_ERROR_ELEMENT_NAME = "processingError";
    public static final String RESULT_ELEMENT_NAME = "result";
    public static final String TRANSACTION_ID_ELEMENT_NAME = "transactionId";
    public static final String VALUE_ELEMENT_NAME = "value";
    public static final String VIEW_ELEMENT_NAME = "view";
    public static final String WORK_BUNDLE_ID_ELEMENT_NAME = "workBundleId";
    public static final String SETUP_ELEMENT_NAME = "setup";
    public static final String NAME_ELEMENT_NAME = "name";

    // common XML element prefixes
    public static final String ATTACHMENT_ELEMENT_PREFIX = "att";
    public static final String EXTRACTED_RECORD_ELEMENT_PREFIX = "extract";

    /**
     * A map of element names of IBaseDataObject methods that get/set primitives and their default values.
     */
    public static final Map<String, Object> PRIMITVE_NAME_DEFAULT_MAP = Collections
            .unmodifiableMap(new ConcurrentHashMap<>(Stream.of(
                    new AbstractMap.SimpleEntry<>(BIRTH_ORDER_ELEMENT_NAME, new BaseDataObject().getBirthOrder()),
                    new AbstractMap.SimpleEntry<>(BROKEN_ELEMENT_NAME, new BaseDataObject().isBroken()),
                    new AbstractMap.SimpleEntry<>(NUM_CHILDREN_ELEMENT_NAME, new BaseDataObject().getNumChildren()),
                    new AbstractMap.SimpleEntry<>(NUM_SIBLINGS_ELEMENT_NAME, new BaseDataObject().getNumSiblings()),
                    new AbstractMap.SimpleEntry<>(OUTPUTABLE_ELEMENT_NAME, new BaseDataObject().isOutputable()),
                    new AbstractMap.SimpleEntry<>(PRIORITY_ELEMENT_NAME, new BaseDataObject().getPriority()))
                    .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue))));

    private AnswerXMLFileElementNames() {}
}
