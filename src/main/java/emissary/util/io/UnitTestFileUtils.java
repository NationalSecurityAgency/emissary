package emissary.util.io;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class UnitTestFileUtils {
    public static void cleanupDirectoryRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static List<Path> findFilesWithRegex(Path path, String regex) throws Exception {
        List<Path> paths = new ArrayList<Path>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path, regex)) {
            for (Path p : dirStream) {
                paths.add(p);
            }
        }
        return paths;
    }

    /** This class is not meant to be instantiated. */
    private UnitTestFileUtils() {}
}
