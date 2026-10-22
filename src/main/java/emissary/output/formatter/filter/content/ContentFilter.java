package emissary.output.formatter.filter.content;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.formatter.filter.Filter;
import emissary.output.formatter.filter.FilterConfigKeys;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base for content (view) filtering.
 */
public abstract class ContentFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ContentFilter.class);

    @Nullable
    private final String listParam;
    private final Set<String> entries = new HashSet<>();
    private final Set<String> wildcardEntries = new HashSet<>();
    private boolean matchAll;
    private Pattern filetypeFormatPattern;
    private Pattern viewNameFormatPattern;

    /**
     * @param listParam the config parameter holding this list's entries (e.g. {@code DENYLIST} or {@code ALLOWLIST})
     */
    protected ContentFilter(final String listParam) {
        this.listParam = listParam;
    }

    protected ContentFilter() {
        this.listParam = null;
    }

    /**
     * Determine if a view may be output for the given payload.
     *
     * @param d the payload
     * @param viewName the view name
     * @return true to allow the view
     */
    public boolean allows(IBaseDataObject d, String viewName) {
        return true;
    }

    /**
     * Parse the list entries from the configuration.
     *
     * @param config the formatter configuration
     */
    @Override
    public void configure(final Configurator config) {
        if (config == null || this.listParam == null) {
            return;
        }
        String allowedNameChars = config.findStringEntry(FilterConfigKeys.ALLOWED_NAME_CHARS, "a-zA-Z0-9_\\-");
        String filetypeFormat = config.findStringEntry(FilterConfigKeys.FILETYPE_FORMAT, "^[%s]+$");
        filetypeFormatPattern = Pattern.compile(filetypeFormat.replace("%s", allowedNameChars));
        String viewNameFormat = config.findStringEntry(FilterConfigKeys.VIEW_NAME_FORMAT, "^[%s]+(\\.[%s]+)?\\*?$");
        viewNameFormatPattern = Pattern.compile(viewNameFormat.replace("%s", allowedNameChars));

        for (String entry : config.findEntriesAsSet(this.listParam)) {
            if (entry.equals("*")) {
                this.matchAll = true;
                continue;
            }
            String viewName = validateAndRemoveFiletype(entry);
            if (!matchesViewNameFormatPattern(viewName)) {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` "
                        + "entry `%s` must match pattern `%s`.", this.listParam, entry, entry, getViewNameFormat()));
            }
            if (viewName.chars().anyMatch(ch -> ch == '.')) {
                logger.warn("`{} = \"{}\"` viewName `{}` should not contain any `.` characters", this.listParam, entry, viewName);
            }

            if (viewName.endsWith("*")) {
                this.wildcardEntries.add(entry.substring(0, entry.length() - 1));
            } else {
                this.entries.add(entry);
            }
        }

        logger.debug("Loaded {} {} list entries and {} wildcard entries", this.entries.size(), this.listParam,
                this.wildcardEntries.size());
    }

    /**
     * Determine if the given view matches one of the configured list entries.
     *
     * @param fileType the payload filetype
     * @param viewName the view name
     * @return true when a configured entry matches
     */
    protected final boolean listContains(final String fileType, final String viewName) {
        if (this.matchAll) {
            return true;
        }
        String fullName = fileType + "." + viewName;
        if (this.entries.contains(viewName) || this.entries.contains(fullName)) {
            return true;
        }
        return this.wildcardEntries.stream().anyMatch(i -> viewName.startsWith(i) || fullName.startsWith(i));
    }

    private String validateAndRemoveFiletype(final String entry) {
        String[] names = entry.split("\\.", 2);

        if (names.length > 1) {
            String filetype = names[0];
            String viewName = names[1];

            if (filetype.equals("*")) {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` "
                        + "wildcarded filetypes not allowed - Did you mean `%s = \"%s\"`?", this.listParam, entry,
                        this.listParam, viewName));
            } else if (!matchesFiletypeFormatPattern(filetype)) {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `%s = \"%s\"` "
                        + "filetype `%s` must match pattern `%s`", this.listParam, entry, filetype, getFiletypeFormat()));
            }
            return viewName;
        }
        return entry;
    }

    private boolean matchesViewNameFormatPattern(final String str) {
        return viewNameFormatPattern.matcher(str).matches();
    }

    public String getViewNameFormat() {
        return viewNameFormatPattern.pattern();
    }

    private boolean matchesFiletypeFormatPattern(final String str) {
        return filetypeFormatPattern.matcher(str).matches();
    }

    public String getFiletypeFormat() {
        return filetypeFormatPattern.pattern();
    }
}
