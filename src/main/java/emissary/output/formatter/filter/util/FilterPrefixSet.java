package emissary.output.formatter.filter.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A set of {@code prefix*} wildcard entries backed by a trie, so {@link #matchesPrefix} walks the input once instead of
 * scanning every configured wildcard. An empty set never matches.
 */
public final class FilterPrefixSet {

    private final Node root = new Node();
    private final Set<String> prefixes = new TreeSet<>();

    /** Add a prefix (without the trailing {@code *}). Empty prefixes are ignored. */
    public void add(final String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        Node node = this.root;
        for (int i = 0; i < prefix.length(); i++) {
            node = node.children.computeIfAbsent(prefix.charAt(i), c -> new Node());
        }
        node.endOfPrefix = true;
        this.prefixes.add(prefix);
    }

    /** Whether any stored prefix is a prefix of {@code name}. */
    public boolean matchesPrefix(final String name) {
        if (name == null) {
            return false;
        }
        Node node = this.root;
        for (int i = 0; i < name.length(); i++) {
            node = node.children.get(name.charAt(i));
            if (node == null) {
                return false;
            }
            if (node.endOfPrefix) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return this.root.children.isEmpty();
    }

    /** Stored prefixes with {@code *} re-appended, for {@code describe()} output. */
    public Set<String> describe() {
        Set<String> all = new TreeSet<>();
        this.prefixes.forEach(w -> all.add(w + "*"));
        return all;
    }

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        boolean endOfPrefix;
    }
}
