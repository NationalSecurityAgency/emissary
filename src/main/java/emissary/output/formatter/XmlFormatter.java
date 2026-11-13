package emissary.output.formatter;

import emissary.core.IBaseDataObject;
import emissary.util.PayloadUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/** XML formatter. */
public class XmlFormatter extends AbstractFormatter {

    @Override
    protected String defaultName() {
        return "XML";
    }

    @Override
    public void writeTo(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        out.write(PayloadUtil.toXmlString(list).getBytes(UTF_8));
    }
}
