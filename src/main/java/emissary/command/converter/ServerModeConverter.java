package emissary.command.converter;

import emissary.directory.EmissaryNode;

import picocli.CommandLine.ITypeConverter;

public class ServerModeConverter implements ITypeConverter<EmissaryNode.EmissaryMode> {

    @Override
    public EmissaryNode.EmissaryMode convert(String s) throws Exception {
        switch (s.toLowerCase()) {
            case "cluster":
                return EmissaryNode.EmissaryMode.CLUSTER;
            case "standalone":
                return EmissaryNode.EmissaryMode.STANDALONE;
            default:
                throw new IllegalArgumentException("Unknown mode: " + s);
        }
    }
}
