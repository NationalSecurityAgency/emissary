package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Accepts everything by default; parses the configured deny entries from the configuration.
 */
public class ContentDenyList {

    private static final Logger logger = LoggerFactory.getLogger(ContentDenyList.class);

    private Pattern filetypeFormatPattern;
    private Pattern viewNameFormatPattern;
    private final Set<String> denylist = new HashSet<>();
    private final Set<String> wildCardDenylist = new HashSet<>();

    /**
     * Parse the view deny-list configuration.
     *
     * @param config the formatter configuration
     */
    public void configure(final Configurator config) {
        if (config == null) {
            return;
        }
        String allowedNameChars = config.findStringEntry("DENYLIST_ALLOWED_NAME_CHARS", "a-zA-Z0-9_\\-");
        String filetypeFormat = config.findStringEntry("DENYLIST_FILETYPE_FORMAT", "^[%s]+$");
        filetypeFormatPattern = Pattern.compile(filetypeFormat.replace("%s", allowedNameChars));
        String viewNameFormat = config.findStringEntry("DENYLIST_VIEW_NAME_FORMAT", "^[%s]+(\\.[%s]+)?\\*?$");
        viewNameFormatPattern = Pattern.compile(viewNameFormat.replace("%s", allowedNameChars));

        for (String entry : config.findEntriesAsSet("DENYLIST")) {
            String viewName = validateAndRemoveDenylistFiletype(entry);
            if (matchesDenylistViewNameFormatPattern(viewName)) {
                if (viewName.chars().anyMatch(ch -> ch == '.')) {
                    logger.warn("`DENYLIST = \"{}\"` viewName `{}` should not contain any `.` characters", entry, viewName);
                }

                if (viewName.endsWith("*")) {
                    this.wildCardDenylist.add(entry.substring(0, entry.length() - 1));
                } else {
                    this.denylist.add(entry);
                }
            } else {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `DENYLIST = \"%s\"` "
                        + "entry `%s` must match pattern `%s`.", entry, entry, getDenylistViewNameFormat()));
            }
        }

        logger.debug("Loaded {} deny list entries and {} wildcard entries", this.denylist.size(), this.wildCardDenylist.size());
    }

    /**
     * Determine if a view may be output, given the payload's filetype.
     *
     * @param d the payload
     * @param viewName the view name
     * @return true to allow the view
     */
    public boolean isAllowed(final IBaseDataObject d, final String viewName) {
        return !denyListContains(DropOffUtil.getFileType(d), viewName);
    }

    private boolean denyListContains(final String fileType, final String viewName) {
        String fullName = fileType + "." + viewName;
        if (this.denylist.contains(viewName) || this.denylist.contains(fullName)) {
            return true;
        }
        return this.wildCardDenylist.stream().anyMatch(i -> viewName.startsWith(i) || fullName.startsWith(i));
    }

    private String validateAndRemoveDenylistFiletype(final String entry) {
        String[] names = entry.split("\\.", 2);

        if (names.length > 1) {
            String filetype = names[0];
            String viewName = names[1];

            if (filetype.equals("*")) {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `DENYLIST = \"%s\"` "
                        + "wildcarded filetypes not allowed in deny list - Did you mean `DENYLIST = \"%s\"`?", entry, viewName));
            } else if (!matchesDenylistFiletypeFormatPattern(filetype)) {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: `DENYLIST = \"%s\"` "
                        + "filetype `%s` must match pattern `%s`", entry, filetype, getDenylistFiletypeFormat()));
            }
            return viewName;
        }
        return entry;
    }

    private boolean matchesDenylistViewNameFormatPattern(String str) {
        return viewNameFormatPattern.matcher(str).matches();
    }

    String getDenylistViewNameFormat() {
        return viewNameFormatPattern.pattern();
    }

    private boolean matchesDenylistFiletypeFormatPattern(String str) {
        return filetypeFormatPattern.matcher(str).matches();
    }

    String getDenylistFiletypeFormat() {
        return filetypeFormatPattern.pattern();
    }
}
