package emissary.util;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Deprecated
public class FileComparator implements Comparator<File>, Serializable {

    // Serializable
    static final long serialVersionUID = 6856435088770934665L;

    @Deprecated
    @Override
    public int compare(File f1, File f2) {

        if (f1.length() == f2.length()) {
            return 0;
        } else if (f1.length() > f2.length()) {
            return 1;
        } else {
            return -1;
        }
    }

    @Deprecated
    public boolean equals(Object o1, Object o2) {
        File f1 = (File) o1;
        File f2 = (File) o2;

        if (f1.length() == f2.length()) {
            return true;
        }
        return false;
    }

    @Deprecated
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: FileComparator input_directory");
            return;
        }

        FileComparator fc = new FileComparator();

        File dir = new File(args[0]);
        File[] fileList = dir.listFiles();
        List<File> fileVector = new ArrayList<File>();
        for (int i = 0; i < fileList.length; i++) {
            // System.out.println(fileList[i].getName() + " " + fileList[i].length());
            fileVector.add(fileList[i]);
        }
        System.out.println("Sorting...");

        Collections.sort(fileVector, fc);

        for (int i = 0; i < fileVector.size(); i++) {
            File f = fileVector.get(i);
            System.out.println(f.getName() + " " + f.length());
        }
    }
}
