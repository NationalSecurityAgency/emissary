package emissary.output.policy.rule;

import emissary.core.EmissaryRuntimeException;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.policy.request.ViewRequest;

import jakarta.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses one {@code POLICY_STAGE} rule.
 *
 * <p>
 * Grammar: {@code <allow|deny> <payload|view|param|value> <selector>}. Parsing is strict and fails fast, naming the
 * offending stage text, so a typo cannot silently change what is output.
 */
public final class RuleParser {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.\\-]+");
    private static final Pattern WILDCARD_NAME = Pattern.compile("[A-Za-z0-9_.\\-]+\\*?");
    private static final Pattern FILE_TYPE = Pattern.compile("[A-Za-z0-9_\\-]+");

    private RuleParser() {}

    /**
     * Parse a rule.
     *
     * @param text the full stage text
     * @param index the 1-based rule number, used in traces
     * @return the parsed rule
     * @throws EmissaryRuntimeException when the rule is malformed
     */
    public static Rule parse(final String text, final int index) {
        if (text == null || text.trim().isEmpty()) {
            throw invalid(text, "rule is empty");
        }
        final String trimmed = text.trim();
        final String[] parts = trimmed.split("\\s+", 3);
        if (parts.length < 3) {
            throw invalid(trimmed, "expected '<allow|deny> <payload|view|param|value> <selector>'");
        }
        final Outcome action = parseAction(trimmed, parts[0]);
        final String dimension = parts[1].toLowerCase(Locale.ROOT);
        final String selector = parts[2].trim();
        switch (dimension) {
            case "payload":
                return parsePayload(trimmed, index, action, selector);
            case "view":
                return parseView(trimmed, index, action, selector);
            case "param":
                return parseParam(trimmed, index, action, selector);
            case "value":
                return parseValue(trimmed, index, action, selector);
            default:
                throw invalid(trimmed, "unknown dimension '" + parts[1] + "'");
        }
    }

    private static Outcome parseAction(final String text, final String token) {
        if ("allow".equalsIgnoreCase(token)) {
            return Outcome.ALLOW;
        }
        if ("deny".equalsIgnoreCase(token)) {
            return Outcome.DENY;
        }
        throw invalid(text, "action must be 'allow' or 'deny'");
    }

    private static Rule parsePayload(final String text, final int index, final Outcome action, final String selector) {
        if (isAll(selector) || "any".equalsIgnoreCase(selector)) {
            return Rule.payload(index, action, text, request -> true);
        }
        final Set<String> fileTypes = new HashSet<>();
        final Set<String> fileTypePrefixes = new HashSet<>();
        final Set<String> forms = new HashSet<>();
        final Set<String> formPrefixes = new HashSet<>();
        Boolean outputable = null;
        boolean matchedClause = false;

        for (final String clause : selector.split("\\s+")) {
            if (clause.isEmpty()) {
                continue;
            }
            final int eq = clause.indexOf('=');
            if (eq < 0) {
                throw invalid(text, "payload clause '" + clause + "' must be key=value");
            }
            final String key = clause.substring(0, eq).toLowerCase(Locale.ROOT);
            final String value = clause.substring(eq + 1);
            matchedClause = true;
            switch (key) {
                case "filetype":
                    addValues(text, value, fileTypes, fileTypePrefixes, FILE_TYPE);
                    break;
                case "form":
                    addValues(text, value, forms, formPrefixes, FILE_TYPE);
                    break;
                case "outputable":
                    outputable = parseBoolean(text, value);
                    break;
                default:
                    throw invalid(text, "unknown payload clause '" + key + "'");
            }
        }
        if (!matchedClause) {
            throw invalid(text, "payload selector is empty");
        }
        final PayloadSelector parsed = new PayloadSelector(fileTypes, fileTypePrefixes, forms, formPrefixes, outputable);
        return Rule.payload(index, action, text, parsed::matches);
    }

    private static Rule parseView(final String text, final int index, final Outcome action, final String selector) {
        if (isAll(selector)) {
            return Rule.view(index, action, text, request -> true);
        }
        final Set<String> exact = new HashSet<>();
        final Set<String> prefixes = new HashSet<>();
        boolean matchAll = false;

        for (final String raw : selector.split(",", -1)) {
            final String entry = raw.trim();
            if (entry.isEmpty()) {
                throw invalid(text, "empty view entry");
            }
            if (isAll(entry)) {
                matchAll = true;
                continue;
            }
            validateWildcardName(text, entry, "view");
            validateViewFileType(text, entry);
            if (entry.endsWith("*")) {
                prefixes.add(entry.substring(0, entry.length() - 1));
            } else {
                exact.add(entry);
            }
        }
        final ViewSelector parsed = new ViewSelector(matchAll, exact, prefixes);
        return Rule.view(index, action, text, parsed::matches);
    }

    private static Rule parseParam(final String text, final int index, final Outcome action, final String selector) {
        if (isAll(selector)) {
            return Rule.param(index, action, text, request -> true);
        }
        final Set<String> exact = new HashSet<>();
        final Set<String> prefixes = new HashSet<>();
        boolean matchAll = false;

        for (final String raw : selector.split(",", -1)) {
            final String entry = raw.trim();
            if (entry.isEmpty()) {
                throw invalid(text, "empty parameter entry");
            }
            if (isAll(entry)) {
                matchAll = true;
                continue;
            }
            validateWildcardName(text, entry, "parameter");
            if (entry.endsWith("*")) {
                prefixes.add(entry.substring(0, entry.length() - 1));
            } else {
                exact.add(entry);
            }
        }
        final NameSelector parsed = new NameSelector(matchAll, exact, prefixes);
        return Rule.param(index, action, text, request -> parsed.matches(request.key()));
    }

    private static Rule parseValue(final String text, final int index, final Outcome action, final String selector) {
        final ValueSelector.Builder builder = new ValueSelector.Builder();
        for (final String raw : selector.split(",", -1)) {
            final String entry = raw.trim();
            if (entry.isEmpty()) {
                throw invalid(text, "empty value entry");
            }
            final int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                throw invalid(text, "value entry '" + entry + "' must be KEY=PATTERN");
            }
            final String key = entry.substring(0, eq).trim();
            final String value = entry.substring(eq + 1).trim();
            if (isAll(key)) {
                builder.add("*", value);
            } else {
                validateWildcardName(text, key, "value key");
                builder.add(key, value);
            }
        }
        final ValueSelector parsed = builder.build();
        return Rule.value(index, action, text, request -> parsed.matches(request.key(), request.value()));
    }

    private static void addValues(final String text, final String raw, final Set<String> exact, final Set<String> prefixes,
            final Pattern pattern) {
        for (final String entry : raw.split(",", -1)) {
            final String value = entry.trim();
            if (value.isEmpty()) {
                throw invalid(text, "empty value in list");
            }
            final String base = value.endsWith("*") ? value.substring(0, value.length() - 1) : value;
            if (base.isEmpty() || !pattern.matcher(base).matches()) {
                throw invalid(text, "invalid match value '" + value + "'");
            }
            if (value.endsWith("*")) {
                prefixes.add(base);
            } else {
                exact.add(value);
            }
        }
    }

    private static void validateWildcardName(final String text, final String entry, final String kind) {
        if (!WILDCARD_NAME.matcher(entry).matches()) {
            throw invalid(text, kind + " entry '" + entry + "' contains unsupported characters");
        }
    }

    private static void validateViewFileType(final String text, final String entry) {
        final String base = entry.endsWith("*") ? entry.substring(0, entry.length() - 1) : entry;
        final int dot = base.indexOf('.');
        if (dot < 0) {
            if (!NAME.matcher(base).matches()) {
                throw invalid(text, "view entry '" + entry + "' is invalid");
            }
            return;
        }
        final String fileType = base.substring(0, dot);
        final String viewName = base.substring(dot + 1);
        if (!FILE_TYPE.matcher(fileType).matches() || !NAME.matcher(viewName).matches()) {
            throw invalid(text, "view entry '" + entry + "' is invalid; expected [FILETYPE.]VIEW[*]");
        }
    }

    private static boolean parseBoolean(final String text, final String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw invalid(text, "outputable must be 'true' or 'false'");
    }

    private static boolean isAll(final String value) {
        return "*".equals(value) || "ALL".equalsIgnoreCase(value);
    }

    private static EmissaryRuntimeException invalid(final String text, final String reason) {
        return new EmissaryRuntimeException("Invalid POLICY_STAGE \"" + text + "\": " + reason);
    }

    @SuppressWarnings("ImmutableMemberCollection")
    private static class NameSelector {
        private final boolean matchAll;
        private final Set<String> exact;
        private final Set<String> prefixes;

        NameSelector(final boolean matchAll, final Set<String> exact, final Set<String> prefixes) {
            this.matchAll = matchAll;
            this.exact = Set.copyOf(exact);
            this.prefixes = Set.copyOf(prefixes);
        }

        boolean matches(final String name) {
            return this.matchAll || this.exact.contains(name) || this.prefixes.stream().anyMatch(name::startsWith);
        }
    }

    private static final class ViewSelector extends NameSelector {
        ViewSelector(final boolean matchAll, final Set<String> exact, final Set<String> prefixes) {
            super(matchAll, exact, prefixes);
        }

        boolean matches(final ViewRequest request) {
            return matches(request.viewName()) || matches(request.payload().fileType() + "." + request.viewName());
        }
    }

    private static final class PayloadSelector {
        private final NameSelector fileTypes;
        private final NameSelector forms;
        @Nullable
        private final Boolean outputable;

        PayloadSelector(final Set<String> fileTypes, final Set<String> fileTypePrefixes, final Set<String> forms,
                final Set<String> formPrefixes, @Nullable final Boolean outputable) {
            this.fileTypes = new NameSelector(fileTypes.isEmpty() && fileTypePrefixes.isEmpty(), fileTypes, fileTypePrefixes);
            this.forms = new NameSelector(forms.isEmpty() && formPrefixes.isEmpty(), forms, formPrefixes);
            this.outputable = outputable;
        }

        boolean matches(final PayloadRequest request) {
            return this.fileTypes.matches(request.fileType()) && this.forms.matches(request.form())
                    && (this.outputable == null || this.outputable == request.outputable());
        }
    }

    @SuppressWarnings("ImmutableMemberCollection")
    private static final class ValueSelector {
        private final Map<String, List<String>> keys;

        private ValueSelector(final Map<String, List<String>> keys) {
            final Map<String, List<String>> copy = new HashMap<>();
            keys.forEach((key, patterns) -> copy.put(key, List.copyOf(patterns)));
            this.keys = Map.copyOf(copy);
        }

        boolean matches(final String key, final Object value) {
            final String text = String.valueOf(value);
            return this.keys.entrySet().stream().anyMatch(entry -> {
                final String selector = entry.getKey();
                final boolean matchesKey = selector.endsWith("*") ? key.startsWith(selector.substring(0, selector.length() - 1))
                        : key.equals(selector);
                return matchesKey && entry.getValue().stream().anyMatch(pattern -> FilenameUtils.wildcardMatch(text, pattern));
            });
        }

        private static final class Builder {
            private final Map<String, List<String>> keys = new HashMap<>();

            void add(final String key, final String pattern) {
                this.keys.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pattern);
            }

            ValueSelector build() {
                return new ValueSelector(this.keys);
            }
        }
    }
}
