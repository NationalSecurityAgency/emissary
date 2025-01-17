package emissary.command.converter;

import emissary.directory.EmissaryNode;

import picocli.CommandLine.ITypeConverter;

public class ModeConverter implements ITypeConverter<EmissaryNode.Mode> {

    @Override
    public EmissaryNode.Mode convert(String s) throws Exception {
        switch (s.toLowerCase()) {
            case "cluster":
                return EmissaryNode.Mode.CLUSTER;
            case "standalone":
                return EmissaryNode.Mode.STANDALONE;
            default:
                throw new IllegalArgumentException("Unknown mode: " + s);
        }
    }
}
