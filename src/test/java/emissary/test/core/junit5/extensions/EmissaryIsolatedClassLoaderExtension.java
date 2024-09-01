package emissary.test.core.junit5.extensions;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.commons.util.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;

public class EmissaryIsolatedClassLoaderExtension implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        if (extensionContext.getRequiredTestClass().getClassLoader().getClass().getName().equals(TestClassLoader.class.getName())) {
            invocation.proceed();
            return;
        }
        invocation.skip();
        runTestWithIsolatedClassPath(invocationContext, extensionContext);
    }

    private static void runTestWithIsolatedClassPath(ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        // get the test class and method
        Class<?> testClass = extensionContext.getRequiredTestClass();
        Method testMethod = invocationContext.getExecutable();

        // get the original and isolated class loaders
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        @SuppressWarnings("BanClassLoader")
        URLClassLoader isolatedClassLoader = new TestClassLoader();

        // set the isolated class loader
        Thread.currentThread().setContextClassLoader(isolatedClassLoader);
        try {
            // run test using isolated class loader
            Class<?> isolatedTestClass = isolatedClassLoader.loadClass(testClass.getName());
            Object testInstance = ReflectionUtils.newInstance(isolatedTestClass);
            Method method = ReflectionUtils.findMethod(isolatedTestClass, testMethod.getName())
                    .orElseThrow(() -> new UnsupportedOperationException("Method not found " + testMethod.getName()));
            ReflectionUtils.invokeMethod(method, testInstance);
        } finally {
            // revert to the original class loader
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @SuppressWarnings("BanClassLoader")
    public static class TestClassLoader extends URLClassLoader {
        public TestClassLoader() {
            super(getUrlsFromSystemClassLoader());
        }

        private static URL[] getUrlsFromSystemClassLoader() {
            ClassLoader systemClassLoader = getSystemClassLoader();
            if (systemClassLoader instanceof URLClassLoader) {
                return ((URLClassLoader) systemClassLoader).getURLs();
            }
            String[] paths = System.getProperty("java.class.path").split("[" + File.pathSeparator + "]");
            return Arrays.stream(paths).map(path -> {
                try {
                    return Paths.get(path).toUri().toURL();
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException("Invalid class path item: " + path, e);
                }
            }).toArray(URL[]::new);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("emissary")) {
                return super.findClass(name);
            }
            return super.loadClass(name);
        }
    }
}
