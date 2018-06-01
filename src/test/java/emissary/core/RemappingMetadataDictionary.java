package emissary.core;

public class RemappingMetadataDictionary extends MetadataDictionary {
    @Override
    public String map(final String s) {
        if ("FOO".equals(s)) {
            return "BAR";
        }
        return s.toLowerCase();
    }
}
