package emissary.output.filter;

import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Deny-first filter that only governs whole-payload eligibility.
 */
public class PayloadFilterCondition implements IFilterCondition, Predicate<IBaseDataObject> {

    private static final Logger logger = LoggerFactory.getLogger(PayloadFilterCondition.class);

    private Pattern filetypeFormatPattern;

    private final Set<String> denyFiletypes = new HashSet<>();
    private final Set<String> denyFiletypesWildcard = new HashSet<>();
    private final Set<String> denyForms = new HashSet<>();
    private final Set<String> denyFormsWildcard = new HashSet<>();

    @Override
    public void initialize(@Nullable final Configurator configG) {
        if (configG != null) {
            configure(configG);
        }
    }

    private void configure(final Configurator filterConfig) {
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
    public boolean accept(final IBaseDataObject payload) {
        return test(payload);
    }

    @Override
    public boolean accept(final List<IBaseDataObject> payloads) {
        return payloads.stream().allMatch(this);
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

    private static boolean matches(String target, Set<String> explicit, Set<String> wildcards) {
        if (target == null) {
            return false;
        }
        if (explicit.contains(target)) {
            return true;
        }
        return wildcards.stream().anyMatch(target::startsWith);
    }
}
