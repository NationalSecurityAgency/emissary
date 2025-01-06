package emissary.util.os;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class OSReleaseUtil {

    private static final Path OS_RELEASE_PATH = Path.of("/etc/os-release");

    private OSReleaseUtil() {}

    /**
     * Finds and parses the VERSION_ID entry in the /etc/os-release file
     *
     * @return the VERSION_ID value
     */
    public static String getVersionId() {
        return getVersionId(OS_RELEASE_PATH);
    }

    static String getVersionId(Path osReleasePath) {
        String versionId = "UNKNOWN";

        if (Files.exists(osReleasePath)) {
            try (Stream<String> lines = Files.lines(osReleasePath)) {
                Optional<String> versionIdOptional = lines.filter(line -> StringUtils.startsWith(line, "VERSION_ID")).findFirst();
                if (versionIdOptional.isPresent()) {
                    String versionIdLine = versionIdOptional.get().replace("\"", "");
                    versionId = versionIdLine.substring(versionIdLine.indexOf("=") + 1);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return versionId;
    }

    /**
     * Uses the VERSION_ID entry in the /etc/os-release file to determine the major OS version
     *
     * @return the major OS version
     */
    public static String getMajorVersion() {
        return getMajorVersion(OS_RELEASE_PATH);
    }

    static String getMajorVersion(Path osReleasePath) {
        return String.format("%.0f", Float.parseFloat(getVersionId(osReleasePath)));
    }

    /**
     * Use the /etc/os-release file to determine if the OS is Ubuntu
     *
     * @return true if ubuntu is found, false otherwise
     */
    public static boolean isUbuntu() {
        return isUbuntu(OS_RELEASE_PATH);
    }

    static boolean isUbuntu(Path osReleasePath) {
        if (Files.exists(osReleasePath)) {
            try (Stream<String> lines = Files.lines(osReleasePath)) {
                return lines.anyMatch(s -> StringUtils.containsIgnoreCase(s, "ubuntu"));
            } catch (Exception e) {
                // ignore
            }
        }
        return false;
    }
}
