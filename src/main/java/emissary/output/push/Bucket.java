package emissary.output.push;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * Configuration information about a configured output bucket to be Pushed.
 */
public final class Bucket {
    final String key;
    final Path source;
    final PathMatcher filter;

    public Bucket(String key, Path source, PathMatcher filter) {
        this.key = key;
        this.source = source;
        this.filter = filter;
    }

    public String getKey() {
        return key;
    }

    public Path getSource() {
        return source;
    }

    public PathMatcher getFilter() {
        return filter;
    }

    /**
     * Returns a Filter&lt;Path&gt; using the PathMatcher
     * 
     * @return a DirectoryStream.Filter for the stream
     */
    public DirectoryStream.Filter<Path> getStreamFilter() {
        return new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) {
                return filter.matches(entry.getFileName());
            }
        };
    }

    @Override
    public String toString() {
        return "Bucket{" + "key=" + key + ", source=" + source + ", filter=" + filter + '}';
    }

}
