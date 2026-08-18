package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.io.DateFilterFilenameGenerator;
import emissary.util.PayloadUtil;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * XML output formatter serializing payloads via {@link PayloadUtil#toXmlString(List)}.
 */
public class XmlFormatter extends AbstractRollableFormatter {

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator formatterConfig) {
        if (name == null) {
            setName("XML");
        }
        super.initialize(configG, name, formatterConfig);
        this.appendNewLine = false;
    }

    @Override
    protected void initFilenameGenerator() {
        this.fileNameGenerator = new DateFilterFilenameGenerator(".xml");
    }

    @Override
    public void writeTo(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        out.write(PayloadUtil.toXmlString(list).getBytes(UTF_8));
    }
}
