package emissary.output.sink;

import emissary.core.IBaseDataObject;
import emissary.output.policy.OutputPolicy;
import emissary.output.policy.request.PayloadRequest;
import emissary.util.xml.JDOMUtil;

import org.jdom2.Document;
import org.jdom2.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Writes the existing XML shape, applying policy to original data before reading or encoding denied content. */
final class XmlWriter {
    private final PolicyGate gate;

    XmlWriter(final OutputPolicy policy) {
        this.gate = new PolicyGate(XmlWriter.class, policy);
    }

    void writeTo(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        final Element root = new Element("payload-list");
        for (final IBaseDataObject data : list) {
            root.addContent(payload(data));
        }
        out.write(JDOMUtil.toString(new Document(root)).getBytes(UTF_8));
    }

    private Element payload(final IBaseDataObject data) {
        final PayloadRequest facts = PayloadRequest.from(data);
        final Element root = new Element("payload");
        root.addContent(JDOMUtil.protectedElement("name", data.getFilename()));
        final Element forms = new Element("current-forms");
        for (final String form : data.getAllCurrentForms()) {
            forms.addContent(JDOMUtil.simpleElement("current-form", form));
        }
        root.addContent(forms);
        root.addContent(JDOMUtil.simpleElement("encoding", data.getFontEncoding()));
        root.addContent(JDOMUtil.simpleElement("filetype", data.getFileType()));
        root.addContent(JDOMUtil.simpleElement("classification", data.getClassification()));
        final Element history = new Element("transform-history");
        for (final String step : data.transformHistory(true)) {
            history.addContent(JDOMUtil.simpleElement("itinerary-step", step));
        }
        root.addContent(history);
        if (data.getProcessingError() != null) {
            root.addContent(JDOMUtil.simpleElement("processing-error", data.getProcessingError()));
        }

        final Element metadata = new Element("metadata");
        for (final Map.Entry<String, Collection<Object>> entry : data.getParameters().entrySet()) {
            final String key = entry.getKey();
            if (!this.gate.allowsParam(facts, key)) {
                continue;
            }
            final List<String> values = new ArrayList<>();
            for (final Object value : entry.getValue()) {
                if (this.gate.allowsValue(facts, key, value)) {
                    values.add(String.valueOf(value));
                }
            }
            if (!values.isEmpty()) {
                metadata.addContent(JDOMUtil.protectedElement("param", String.join(IBaseDataObject.DEFAULT_PARAM_SEPARATOR, values))
                        .setAttribute("name", key));
            }
        }
        if (!metadata.getChildren().isEmpty()) {
            root.addContent(metadata);
        }

        if (this.gate.allowsView(facts, OutputPolicy.PRIMARY_VIEW_NAME)) {
            if (data.header() != null) {
                root.addContent(JDOMUtil.protectedElement("header", data.header()));
            }
            if (data.dataLength() > 0) {
                root.addContent(JDOMUtil.protectedElement("data", data.data()));
            }
            if (data.footer() != null) {
                root.addContent(JDOMUtil.protectedElement("footer", data.footer()));
            }
        }
        final Element views = new Element("views");
        for (final String name : data.getAlternateViewNames()) {
            if (this.gate.allowsView(facts, name)) {
                views.addContent(JDOMUtil.protectedElement("view", data.getAlternateView(name)).setAttribute("name", name));
            }
        }
        if (!views.getChildren().isEmpty()) {
            root.addContent(views);
        }
        return root;
    }
}
