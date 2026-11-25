package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;
import emissary.output.formatter.filter.util.FilterPrefixSet;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** List filter for views and parameters. {@code FILTER_MODE} polarity: deny drops listed, allow emits only listed. */
public class FilterOutput extends AbstractItemFilter {

    private static final Logger logger = LoggerFactory.getLogger(FilterOutput.class);

    private boolean deny = true;

    private final ViewList views = new ViewList(ConfigKeys.VIEWS);
    private final ParamList params = new ParamList(ConfigKeys.PARAMS);
    private final Map<String, Set<String>> paramValues = new HashMap<>();
    private final Set<String> stripPrefixes = new TreeSet<>();

    @Override
    public void configure(@Nullable final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        String mode = config.findStringEntry(ConfigKeys.FILTER_MODE, null);
        if (mode != null && "allow".equalsIgnoreCase(mode.trim())) {
            this.deny = false;
        } else if (mode != null && !"deny".equalsIgnoreCase(mode.trim())) {
            logger.warn("Unknown {} = \"{}\", using \"deny\"", ConfigKeys.FILTER_MODE, mode);
        }
        this.views.configure(config);
        this.params.configure(config);
        this.stripPrefixes.addAll(config.findEntriesAsSet(ConfigKeys.STRIP_PARAM_PREFIX));
        this.paramValues.putAll(config.findStringMatchMultiMap(ConfigKeys.PARAM_VALUE));
    }

    @Override
    public boolean test(@Nullable final IBaseDataObject d, final String key, @Nullable final Object value) {
        String name = stripped(key);
        return value == null ? paramAllowed(name) : paramValueAllowed(name, value);
    }

    @Override
    public boolean test(final IBaseDataObject d, final String viewName) {
        return viewAllowed(d, viewName);
    }

    private boolean viewAllowed(@Nullable final IBaseDataObject d, final String viewName) {
        if (this.views.isEmpty()) {
            return this.deny;
        }
        String fileType = d == null ? "" : DropOffUtil.getFileType(d);
        if (this.deny) {
            return !this.views.matches(fileType, viewName);
        }
        return this.views.matches(fileType, viewName);
    }

    private boolean paramAllowed(final String name) {
        if (this.params.isEmpty()) {
            return this.deny;
        }
        if (this.deny) {
            return !this.params.matches(name);
        }
        return this.params.matches(name);
    }

    private boolean paramValueAllowed(final String name, final Object value) {
        Set<String> listed = this.paramValues.get(name);
        if (this.deny) {
            if (!paramAllowed(name)) {
                return false;
            }
            return listed == null || !listed.contains(String.valueOf(value));
        }
        if (!paramAllowed(name)) {
            return false;
        }
        return listed == null || listed.contains(String.valueOf(value));
    }

    private String stripped(final String name) {
        for (final String prefix : this.stripPrefixes) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return name;
    }

    @Override
    public String describe(final boolean verbose) {
        StringBuilder sb = new StringBuilder(super.describe(verbose));
        if (!verbose) {
            return sb.toString();
        }
        sb.append("[").append(ConfigKeys.FILTER_MODE).append(" = ").append(this.deny ? "deny" : "allow");
        this.views.appendTo(sb);
        this.params.appendTo(sb);
        if (!this.paramValues.isEmpty()) {
            sb.append(' ').append(ConfigKeys.PARAM_VALUE).append('*').append(" = ").append(this.paramValues);
        }
        if (!this.stripPrefixes.isEmpty()) {
            sb.append(' ').append(ConfigKeys.STRIP_PARAM_PREFIX).append(" = ").append(String.join(",", this.stripPrefixes));
        }
        return sb.append(']').toString();
    }

    /** Filetype format pattern used for view list validation. */
    public String getFiletypeFormat() {
        return this.views.getFiletypeFormat();
    }

    /** View name format pattern used for view list validation. */
    public String getViewNameFormat() {
        return this.views.getViewNameFormat();
    }

    /** Configured view list: entries are view names, optionally filetype qualified, optionally *-suffixed. */
    static final class ViewList {

        private final String key;
        private final Set<String> entries = new HashSet<>();
        private final FilterPrefixSet wildcardEntries = new FilterPrefixSet();
        private boolean matchAll;
        @Nullable
        private Pattern filetypeFormatPattern;
        @Nullable
        private Pattern viewNameFormatPattern;

        ViewList(final String key) {
            this.key = key;
        }

        void configure(@Nullable final Configurator config) {
            if (config == null) {
                return;
            }
            String allowedNameChars = config.findStringEntry(ConfigKeys.VIEW_NAME_CHARS, "a-zA-Z0-9_\\-");
            String filetypeFormat = config.findStringEntry(ConfigKeys.FILETYPE_FORMAT, "^[%s]+$");
            this.filetypeFormatPattern = Pattern.compile(filetypeFormat.replace("%s", allowedNameChars));
            String viewNameFormat = config.findStringEntry(ConfigKeys.VIEW_NAME_FORMAT, "^[%s]+(\\.[%s]+)?\\*?$");
            this.viewNameFormatPattern = Pattern.compile(viewNameFormat.replace("%s", allowedNameChars));

            for (String entry : config.findEntriesAsSet(this.key)) {
                if (entry.equals("*")) {
                    this.matchAll = true;
                    continue;
                }
                String viewName = validateAndRemoveFiletype(entry);
                if (this.viewNameFormatPattern != null && !this.viewNameFormatPattern.matcher(viewName).matches()) {
                    throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` "
                            + "entry `%s` must match pattern `%s`.", this.key, entry, entry, getViewNameFormat()));
                }
                if (viewName.chars().anyMatch(ch -> ch == '.')) {
                    logger.warn("`{} = \"{}\"` viewName `{}` should not contain any `.` characters", this.key, entry, viewName);
                }
                if (viewName.endsWith("*")) {
                    this.wildcardEntries.add(entry.substring(0, entry.length() - 1));
                } else {
                    this.entries.add(entry);
                }
            }

            logger.debug("Loaded {} {} list entries and {} wildcard entries", this.entries.size(), this.key,
                    this.wildcardEntries.describe().size());
        }

        boolean isEmpty() {
            return !this.matchAll && this.entries.isEmpty() && this.wildcardEntries.isEmpty();
        }

        boolean matches(final String fileType, final String viewName) {
            if (this.matchAll) {
                return true;
            }
            String fullName = fileType + "." + viewName;
            if (this.entries.contains(viewName) || this.entries.contains(fullName)) {
                return true;
            }
            return this.wildcardEntries.matchesPrefix(viewName) || this.wildcardEntries.matchesPrefix(fullName);
        }

        private String validateAndRemoveFiletype(final String entry) {
            String[] names = entry.split("\\.", 2);
            if (names.length > 1) {
                String filetype = names[0];
                String viewName = names[1];
                if (filetype.equals("*")) {
                    throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` "
                            + "wildcarded filetypes not allowed - Did you mean `%s = \"%s\"`?", this.key, entry,
                            this.key, viewName));
                } else if (this.filetypeFormatPattern != null && !this.filetypeFormatPattern.matcher(filetype).matches()) {
                    throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` "
                            + "filetype `%s` must match pattern `%s`", this.key, entry, filetype, getFiletypeFormat()));
                }
                return viewName;
            }
            return entry;
        }

        String getFiletypeFormat() {
            return this.filetypeFormatPattern == null ? "" : this.filetypeFormatPattern.pattern();
        }

        String getViewNameFormat() {
            return this.viewNameFormatPattern == null ? "" : this.viewNameFormatPattern.pattern();
        }

        void appendTo(final StringBuilder sb) {
            if (this.matchAll) {
                sb.append(' ').append(this.key).append(" = *");
            }
            if (!this.entries.isEmpty()) {
                sb.append(' ').append(this.key).append(" = ").append(String.join(",", this.entries));
            }
            if (!this.wildcardEntries.isEmpty()) {
                sb.append(' ').append(this.key).append(" = ").append(String.join(",", this.wildcardEntries.describe()));
            }
        }
    }

    /** Configured parameter list: exact names, {@code name*} prefixes, or {@code *}/{@code ALL}. */
    static final class ParamList {

        private final String key;
        private final Set<String> exact = new TreeSet<>();
        private final FilterPrefixSet wildcards = new FilterPrefixSet();
        private boolean star;

        ParamList(final String key) {
            this.key = key;
        }

        void configure(@Nullable final Configurator config) {
            if (config == null) {
                return;
            }
            for (String entry : config.findEntries(this.key)) {
                if (entry == null || entry.isEmpty()) {
                    continue;
                }
                if (entry.equals("*") || entry.equals("ALL")) {
                    this.star = true;
                } else if (entry.endsWith("*")) {
                    this.wildcards.add(entry.substring(0, entry.length() - 1));
                } else {
                    this.exact.add(entry);
                }
            }
        }

        boolean isEmpty() {
            return !this.star && this.exact.isEmpty() && this.wildcards.isEmpty();
        }

        boolean matches(final String name) {
            if (this.star) {
                return true;
            }
            if (this.exact.contains(name)) {
                return true;
            }
            return this.wildcards.matchesPrefix(name);
        }

        void appendTo(final StringBuilder sb) {
            if (this.star) {
                sb.append(' ').append(this.key).append(" = *");
            }
            if (!this.exact.isEmpty()) {
                sb.append(' ').append(this.key).append(" = ").append(String.join(",", this.exact));
            }
            if (!this.wildcards.isEmpty()) {
                sb.append(' ').append(this.key).append(" = ").append(String.join(",", this.wildcards.describe()));
            }
        }
    }
}
