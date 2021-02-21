package emissary.util.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import emissary.util.shell.Executrix;

public class ListOpenFiles {

    Executrix exec = new Executrix();

    public boolean isOpen(String path) {
        return isOpen(Paths.get(path));
    }

    public boolean isOpen(Path path) {

        boolean fileOpen = false;

        if (Files.exists(path)) {
            int returnVal = exec.execute(new String[] {"lsof", path.toString()});
            if (returnVal == 0) {
                fileOpen = true;
            }
        }

        return fileOpen;
    }
}
