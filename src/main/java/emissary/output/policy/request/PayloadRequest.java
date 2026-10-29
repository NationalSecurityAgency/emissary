package emissary.output.policy.request;

import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;

import java.util.Objects;

/**
 * Facts about a payload that policy stages match against. {@code outputable} is exposed as data so a payload rule can
 * decide on it explicitly and record why, instead of it acting as an invisible gate.
 */
public final class PayloadRequest {

    private final IBaseDataObject payload;
    private final String fileType;
    private final String form;
    private final String language;
    private final boolean outputable;

    /**
     * Create a request.
     *
     * @param payload the payload
     * @param fileType the effective file type
     * @param form the current form
     * @param language the language, or {@code NONE}
     * @param outputable the payload's outputable flag
     */
    public PayloadRequest(final IBaseDataObject payload, final String fileType, final String form, final String language,
            final boolean outputable) {
        this.payload = Objects.requireNonNull(payload, "payload");
        this.fileType = fileType == null ? "" : fileType;
        this.form = form == null ? "" : form;
        this.language = language == null ? "NONE" : language;
        this.outputable = outputable;
    }

    /** Build a request from a payload, computing the standard facts once. */
    public static PayloadRequest from(final IBaseDataObject payload) {
        final String fileType = DropOffUtil.getFileType(payload);
        String language = payload.getParameterAsString("LANGUAGE");
        if (language == null) {
            language = "NONE";
        }
        return new PayloadRequest(payload, fileType, payload.currentForm(), language, payload.isOutputable());
    }

    public IBaseDataObject payload() {
        return this.payload;
    }

    public String fileType() {
        return this.fileType;
    }

    public String form() {
        return this.form;
    }

    public String language() {
        return this.language;
    }

    public boolean outputable() {
        return this.outputable;
    }
}
