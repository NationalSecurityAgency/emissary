package emissary.spi;

import emissary.Emissary;
import emissary.test.core.junit5.UnitTest;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClassLocationVerificationProviderTest extends UnitTest {
    @Test
    void testVerifyClassInWorkingDirectory() {
        String emissaryClassName = Emissary.class.getName();
        boolean status = ClassLocationVerificationProvider.verify(emissaryClassName, "/classes/");
        Assertions.assertTrue(status);

        status = ClassLocationVerificationProvider.verify(emissaryClassName, "doesnotexist");
        Assertions.assertFalse(status);
    }

    @Test
    void testVerifyClassInJar() {
        String javaClassName = StringUtils.class.getName();
        boolean status = ClassLocationVerificationProvider.verify(javaClassName, "commons-lang");
        Assertions.assertTrue(status);

        status = ClassLocationVerificationProvider.verify(javaClassName, "doesnotexist");
        Assertions.assertFalse(status);
    }
}
