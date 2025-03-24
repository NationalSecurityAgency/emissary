package emissary.core.constants;

import emissary.core.IBaseDataObject;
import emissary.core.channels.SeekableByteChannelFactory;

import org.apache.commons.lang3.tuple.Pair;

import java.util.function.BiConsumer;

public final class IbdoMethodNames {
    /**
     * The IBaseDataObject set method name for Birth Order.
     */
    public static final BiConsumer<IBaseDataObject, Integer> SET_BIRTH_ORDER = IBaseDataObject::setBirthOrder;
    /**
     * The IBaseDataObject set method name for Broken.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_BROKEN = IBaseDataObject::setBroken;
    /**
     * The IBaseDataObject set method name for Classification.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_CLASSIFICATION = IBaseDataObject::setClassification;
    /**
     * The IBaseDataObject set method name for Current Form.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_CURRENT_FORM = IBaseDataObject::pushCurrentForm;
    /**
     * The IBaseDataObject set method name for Data.
     */
    public static final BiConsumer<IBaseDataObject, SeekableByteChannelFactory> SET_DATA = IBaseDataObject::setChannelFactory;
    /**
     * The IBaseDataObject set method name for Filename.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_FILENAME = IBaseDataObject::setFilename;
    /**
     * The IBaseDataObject set method name for Font Encoding.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_FONT_ENCODING = IBaseDataObject::setFontEncoding;
    /**
     * The IBaseDataObject set method name for Footer.
     */
    public static final BiConsumer<IBaseDataObject, byte[]> SET_FOOTER = IBaseDataObject::setFooter;
    /**
     * The IBaseDataObject set method name for Header.
     */
    public static final BiConsumer<IBaseDataObject, byte[]> SET_HEADER = IBaseDataObject::setHeader;
    /**
     * The IBaseDataObject set method name for Header Encoding.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_HEADER_ENCODING = IBaseDataObject::setHeaderEncoding;
    /**
     * The IBaseDataObject set method name for Id.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_ID = IBaseDataObject::setId;
    /**
     * The IBaseDataObject set method name for Num Children.
     */
    public static final BiConsumer<IBaseDataObject, Integer> SET_NUM_CHILDREN = IBaseDataObject::setNumChildren;
    /**
     * The IBaseDataObject set method name for Num Sibling.
     */
    public static final BiConsumer<IBaseDataObject, Integer> SET_NUM_SIBLINGS = IBaseDataObject::setNumSiblings;
    /**
     * The IBaseDataObject set method name for Outputable.
     */
    public static final BiConsumer<IBaseDataObject, Boolean> SET_OUTPUTABLE = IBaseDataObject::setOutputable;
    /**
     * The IBaseDataObject set method name for Parameter.
     */
    public static final BiConsumer<IBaseDataObject, Pair<String, CharSequence>> SET_PARAMETER = (b, p) -> b.appendParameter(p.getKey(), p.getValue());
    /**
     * The IBaseDataObject set method name for Priority.
     */
    public static final BiConsumer<IBaseDataObject, Integer> SET_PRIORITY = IBaseDataObject::setPriority;
    /**
     * The IBaseDataObject method name to add Processing Error.
     */
    public static final BiConsumer<IBaseDataObject, String> ADD_PROCESSING_ERROR = IBaseDataObject::addProcessingError;
    /**
     * The IBaseDataObject set method name for Transaction Id.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_TRANSACTION_ID = IBaseDataObject::setTransactionId;
    /**
     * The IBaseDataObject method name to add Alternate View.
     */
    public static final BiConsumer<IBaseDataObject, Pair<String, byte[]>> ADD_ALTERNATE_VIEW =
            (b, p) -> b.addAlternateView(p.getKey(), p.getValue());
    /**
     * The IBaseDataObject set method name for Work Bundle Id.
     */
    public static final BiConsumer<IBaseDataObject, String> SET_WORK_BUNDLE_ID = IBaseDataObject::setWorkBundleId;

    private IbdoMethodNames() {}
}
