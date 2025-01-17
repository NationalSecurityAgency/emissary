package emissary.command.converter;

import emissary.directory.EmissaryNode;

import picocli.CommandLine.ITypeConverter;

import java.util.Locale;

public class ModeConverter implements ITypeConverter<EmissaryNode.Mode> {

    @Override
    public EmissaryNode.Mode convert(String s) throws Exception {
        switch (s.toLowerCase(Locale.getDefault())) {
            case "cluster":
                return EmissaryNode.Mode.CLUSTER;
            case "standalone":
                return EmissaryNode.Mode.STANDALONE;
            default:
                throw new IllegalArgumentException("Unknown mode: " + s);
        }
    }
}
