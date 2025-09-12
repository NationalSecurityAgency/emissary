package emissary.spi;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class ClassLocationVerificationProvider implements InitializationProvider {
    private static final Logger logger = LoggerFactory.getLogger(ClassLocationVerificationProvider.class);

    @Override
    public void initialize() {

        try {
            Configurator configG = ConfigUtil.getConfigInfo(ClassLocationVerificationProvider.class);
            Map<String, String> classNameLocationMap = configG.findStringMatchMap("CLASS_NAME_", true);
            classNameLocationMap.forEach(ClassLocationVerificationProvider::verify);
        } catch (IOException e) {
            logger.debug("Failed to load config", e);
        }

    }

    protected static boolean verify(String className, String expectedClassLoc) {
        boolean success = true;
        try {
            Class<?> clazz = Class.forName(className);
            String classLocPath = clazz.getProtectionDomain().getCodeSource().getLocation().getFile();
            String classLocName = FilenameUtils.getName(classLocPath);

            if (ifArchivedFileNameDoesNotStartsWithExpectedLocation(expectedClassLoc, classLocPath, classLocName)
                    || ifWorkingDirDoesNotContainExpectedLocation(expectedClassLoc, classLocName)) {
                logError(className, expectedClassLoc, classLocPath);
                success = false;
            }
        } catch (ClassNotFoundException e) {
            logger.error("Failed to find class: {}", className);
            success = false;
        }

        return success;
    }

    private static boolean ifWorkingDirDoesNotContainExpectedLocation(String expectedLocation, String locationName) {
        return StringUtils.isNotBlank(locationName) && !Strings.CS.startsWith(locationName, expectedLocation);
    }

    private static boolean ifArchivedFileNameDoesNotStartsWithExpectedLocation(String expectedLocation, String location, String locationName) {
        return StringUtils.isBlank(locationName) && !Strings.CS.contains(location, expectedLocation);
    }

    private static void logError(String className, String expectedLocation, String location) {
        logger.error("Failed to load expected class: {}, actual location: {}, expected location: {}", className, location,
                expectedLocation);
    }
}
