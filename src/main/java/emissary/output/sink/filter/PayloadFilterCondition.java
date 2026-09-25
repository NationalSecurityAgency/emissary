package emissary.output.sink.filter;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;
import emissary.output.formatter.filter.util.FilterPrefixSet;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Default payload filter: denies listed filetypes and forms. */
public class PayloadFilterCondition extends AbstractPayloadFilter implements Predicate<IBaseDataObject> {

    private static final Logger logger = LoggerFactory.getLogger(PayloadFilterCondition.class);

    private Pattern filetypeFormatPattern;

    private final Set<String> denyFiletypes = new HashSet<>();
    private final FilterPrefixSet denyFiletypesWildcard = new FilterPrefixSet();
    private final Set<String> denyForms = new HashSet<>();
    private final FilterPrefixSet denyFormsWildcard = new FilterPrefixSet();

    @Override
    public void configure(@Nullable final Configurator filterConfig) {
        if (filterConfig == null) {
            return;
        }
        String allowedNameChars = filterConfig.findStringEntry("DENYLIST_ALLOWED_NAME_CHARS", "a-zA-Z0-9_\\-");
        String filetypeFormat = filterConfig.findStringEntry("DENYLIST_FILETYPE_FORMAT", "^[%s]+$");
        filetypeFormatPattern = Pattern.compile(filetypeFormat.replace("%s", allowedNameChars));

        for (String entry : filterConfig.findEntriesAsSet("DENY_FILETYPES")) {
            if (!matchesDenylistFiletypeFormatPattern(entry.replace("*", ""))) {
                throw new EmissaryRuntimeException(String.format("Invalid filter configuration: DENY_FILETYPES entry `%s` "
                        + "must match pattern `%s`", entry, getDenylistFiletypeFormat()));
            }
            if (entry.endsWith("*")) {
                denyFiletypesWildcard.add(entry.substring(0, entry.length() - 1));
            } else {
                denyFiletypes.add(entry);
            }
        }

        for (String entry : filterConfig.findEntriesAsSet("DENY_FORMS")) {
            if (entry.endsWith("*")) {
                denyFormsWildcard.add(entry.substring(0, entry.length() - 1));
            } else {
                denyForms.add(entry);
            }
        }
    }

    @Override
    public boolean test(final IBaseDataObject d) {
        String fileType = DropOffUtil.getFileType(d);
        String form = d.currentForm();

        boolean denied = matches(fileType, denyFiletypes, denyFiletypesWildcard) ||
                matches(form, denyForms, denyFormsWildcard);
        if (denied) {
            logger.debug("Rejecting payload due to filetype ({}) or form ({}) match.", fileType, form);
            return false;
        }
        return true;
    }

    private boolean matchesDenylistFiletypeFormatPattern(String str) {
        return filetypeFormatPattern.matcher(str).matches();
    }

    private String getDenylistFiletypeFormat() {
        return filetypeFormatPattern.pattern();
    }

    private static boolean matches(String target, Set<String> explicit, FilterPrefixSet wildcards) {
        if (target == null) {
            return false;
        }
        if (explicit.contains(target)) {
            return true;
        }
        return wildcards.matchesPrefix(target);
    }

    @Override
    public String describe(final boolean verbose) {
        if (!verbose) {
            return super.describe(false);
        }
        return super.describe(false) + " DENY_FILETYPES = " + denyFiletypes
                + (denyFiletypesWildcard.isEmpty() ? "" : "," + String.join(",", denyFiletypesWildcard.describe()))
                + " DENY_FORMS = " + denyForms
                + (denyFormsWildcard.isEmpty() ? "" : "," + String.join(",", denyFormsWildcard.describe()));
    }
}
