package emissary.spi;

import emissary.Emissary;
import emissary.test.core.junit5.UnitTest;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLocationVerificationProviderTest extends UnitTest {
    @Test
    void testVerifyClassInWorkingDirectory() {
        String emissaryClassName = Emissary.class.getName();
        boolean status = ClassLocationVerificationProvider.verify(emissaryClassName, "/classes/");
        assertTrue(status);

        status = ClassLocationVerificationProvider.verify(emissaryClassName, "doesnotexist");
        assertFalse(status);
    }

    @Test
    void testVerifyClassInJar() {
        String javaClassName = StringUtils.class.getName();
        boolean status = ClassLocationVerificationProvider.verify(javaClassName, "commons-lang");
        assertTrue(status);

        status = ClassLocationVerificationProvider.verify(javaClassName, "doesnotexist");
        assertFalse(status);
    }
}
