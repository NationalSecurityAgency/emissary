package emissary.test.core.junit5;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility class to represent a dat file name.
 * 
 * Format should be INITIAL_FORM.FINAL_FORM.OPTIONAL_COMMENT.dat
 */
public class InitialFinalFormFormat {
    // Similar but not same as regex in PayloadUtil
    private static final Pattern validForm = Pattern.compile("([\\w-)(/]+)");

    @Nullable
    private String initialForm;
    @Nullable
    private String finalForm;
    @Nullable
    private String comments;
    private final String baseFileName;
    private final String originalFileName;
    private final Path path;

    public InitialFinalFormFormat(final Path path) {
        this.path = path;
        originalFileName = path.getFileName().toString();
        baseFileName = originalFileName.substring(0, originalFileName.lastIndexOf("."));
        initialForm = null;
        finalForm = null;
        comments = null;
        fillComponents();
    }

    private void fillComponents() {
        final Matcher fileNameMatcher = validForm.matcher(baseFileName);

        if (fileNameMatcher.find()) {
            initialForm = fileNameMatcher.group();
            if (fileNameMatcher.find()) {
                finalForm = fileNameMatcher.group();
                if (fileNameMatcher.find()) {
                    comments = fileNameMatcher.group();
                }
            }
        }
    }

    /**
     * Return the initial portion of a filename, if it exists (null if not)
     *
     * @return null if no initial form exists, or the initial form
     */
    public String getInitialForm() {
        return initialForm;
    }

    /**
     * Return the final form portion of a filename, if it exists (null if not)
     * 
     * @return null if no final form exists, or the final form
     */
    public String getFinalForm() {
        return finalForm;
    }

    /**
     * Returns the comments portion of a filename, if it exists (null if not)
     * 
     * @return null if no comment exists, or the comment
     */
    public String getComments() {
        return comments;
    }

    /**
     * Get base name without extension. e.g. INITIAL_FORM.FINAL_FORM.OPTIONAL_COMMENT.dat becomes
     * INITIAL_FORM.FINAL_FORM.OPTIONAL_COMMENT
     * 
     * @return the base filename
     */
    public String getBaseFileName() {
        return baseFileName;
    }

    /**
     * Returns the original filename
     * 
     * @return the original filename
     */
    public String getOriginalFileName() {
        return originalFileName;
    }

    /**
     * Returns the original full path to the file
     * 
     * @return the original full path to the file
     */
    public Path getPath() {
        return path;
    }
}
