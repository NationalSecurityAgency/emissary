package emissary.util.io;

import emissary.util.shell.Executrix;

import java.nio.file.Files;
import java.nio.file.Path;

public class ListOpenFiles {

    Executrix exec = new Executrix();

    public boolean isOpen(String path) {
        return isOpen(Path.of(path));
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
