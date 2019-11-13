package emissary.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class EmissaryIsolatedClassloaderRunner extends BlockJUnit4ClassRunner {

    public EmissaryIsolatedClassloaderRunner(Class<?> clazz) throws InitializationError {
        super(getFromTestClassloader(clazz));
    }

    private static Class<?> getFromTestClassloader(Class<?> clazz) throws InitializationError {
        try {
            ClassLoader testClassLoader = new TestClassLoader();
            return Class.forName(clazz.getName(), true, testClassLoader);
        } catch (ClassNotFoundException e) {
            throw new InitializationError(e);
        }
    }

    public static class TestClassLoader extends URLClassLoader {
        public TestClassLoader() {
            super(getURLsFromSystemClassLoader());
        }

        private static URL[] getURLsFromSystemClassLoader() {
            ClassLoader systemClassLoader = getSystemClassLoader();
            if (systemClassLoader instanceof URLClassLoader) {
                return ((URLClassLoader) systemClassLoader).getURLs();
            }
            String sep = System.getProperty("path.separator");
            String[] paths = System.getProperty("java.class.path").split("[" + sep + "]");
            return Arrays.stream(paths).map(path -> {
                try {
                    return new File(path).toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException("Invalid class path item: " + path, e);
                }
            }).collect(Collectors.toList()).toArray(new URL[0]);
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
