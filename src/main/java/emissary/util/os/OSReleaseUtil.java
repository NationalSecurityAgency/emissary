package emissary.util.os;

import org.apache.commons.exec.OS;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
            } catch (Exception ignored) {
                // ignore
            }
        } else {
            // fallback to the os.version system property for mac support
            versionId = StringUtils.defaultIfBlank(System.getProperty("os.version"), versionId);
        }
        return versionId;
    }

    /**
     * Uses the VERSION_ID entry in the /etc/os-release file to determine the major OS version
     *
     * @return the major OS version
     */
    public static String getMajorReleaseVersion() {
        return getMajorReleaseVersion(OS_RELEASE_PATH);
    }

    static String getMajorReleaseVersion(Path osReleasePath) {
        try {
            return String.format(Locale.getDefault(), "%.0f", Float.parseFloat(getVersionId(osReleasePath)));
        } catch (NumberFormatException e) {
            // support x.y.z format
            return StringUtils.substringBefore(getVersionId(osReleasePath), ".");
        }
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
        return isOsName(osReleasePath, "ubuntu") || isOsLike(osReleasePath, "ubuntu");
    }

    /**
     * Use the /etc/os-release file to determine if the OS is CentOS
     *
     * @return true if CentOS is found, false otherwise
     */
    public static boolean isCentOs() {
        return isCentOs(OS_RELEASE_PATH);
    }

    static boolean isCentOs(Path osReleasePath) {
        return isOsName(osReleasePath, "centos");
    }

    /**
     * Use the /etc/os-release file to determine if the OS is RHEL
     *
     * @return true if RHEL is found, false otherwise
     */
    public static boolean isRhel() {
        return isRhel(OS_RELEASE_PATH);
    }

    static boolean isRhel(Path osReleasePath) {
        return isOsName(osReleasePath, "rhel");
    }

    /**
     * Determine if the OS is macos
     *
     * @return true if mac, false otherwise
     */
    public static boolean isMac() {
        return OS.isFamilyMac();
    }

    // checks against ID in /etc/os-release
    private static boolean isOsName(Path osReleasePath, String osName) {
        if (Files.exists(osReleasePath)) {
            try (Stream<String> lines = Files.lines(osReleasePath)) {
                return lines.filter(line -> line.startsWith("ID=")).anyMatch(entry -> StringUtils.containsIgnoreCase(entry, osName));
            } catch (IOException ignored) {
                // ignore
            }
        }
        return false;
    }

    // checks against ID_LIKE in /etc/os-release
    private static boolean isOsLike(Path osReleasePath, String osName) {
        if (Files.exists(osReleasePath)) {
            try (Stream<String> lines = Files.lines(osReleasePath)) {
                return lines.filter(line -> line.startsWith("ID_LIKE=")).anyMatch(entry -> StringUtils.containsIgnoreCase(entry, osName));
            } catch (IOException ignored) {
                // ignore
            }
        }
        return false;
    }
}
