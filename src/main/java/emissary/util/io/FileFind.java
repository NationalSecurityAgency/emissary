/*
 * FileFind.java
 *
 * Created on November 12, 2001, 6:29 AM
 */

package emissary.util.io;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Stack;

/**
 * Implements the unix 'find' command. This class lists files within a directory no matter how many levels of
 * subdirectories are used. The basic functionality of find is supported. If you would like the options then go ahead
 * and implement them.
 *
 * @author ce
 * @version 1.0
 */

public class FileFind {

    public static final int NO_OPTIONS = 0x00;
    public static final int FILES_FLAG = 0x01;
    public static final int DIRECTORIES_FLAG = 0x02;

    @SuppressWarnings("unused")
    private boolean wantFiles = true;
    private boolean wantDirectories = false;

    /** Creates new FileFind */
    public FileFind() {}

    /**
     * Creates new FileFind with options
     */
    public FileFind(int options) {
        // default is true
        if ((options & FILES_FLAG) == 0) {
            wantFiles = false;
        }

        // default is false
        if ((options & DIRECTORIES_FLAG) > 0) {
            wantDirectories = true;
        }
    }

    public Iterator<?> find(String filename) throws IOException {
        return new FileIterator(filename);
    }

    public Iterator<?> find(String filename, FileFilter filter) throws IOException {
        return new FileIterator(filename, filter);
    }

    public static void main(String[] args) {

        FileFind ff = new FileFind(FILES_FLAG | DIRECTORIES_FLAG);

        String start = "c:\\Temp";
        if (args.length > 0) {
            start = args[0];
        }


        try {
            Iterator<?> i = ff.find(start);
            while (i.hasNext()) {
                File f = (File) i.next();
                if (f.isDirectory()) {
                    System.out.println("d: " + f.getPath());
                } else {
                    System.out.println("f: " + f.getPath());
                }
            }
        } catch (Exception e) {
            System.out.println("Exception:" + e);
        }
    }


    /** Iterator that iterates through a directory tree. */
    @SuppressWarnings("rawtypes")
    class FileIterator implements Iterator {
        /**
         * Stack of Files and directory lists keeping track of where in the tree we are.
         */
        private Stack<Object> currentPath = new Stack<Object>();
        private FileFilter filter = null;

        public FileIterator(String filename) throws IOException {
            this(filename, null);
        }

        public FileIterator(String filename, FileFilter filter) throws IOException {
            File f = new File(filename);
            if (!f.exists() || !f.canRead()) {
                throw new IOException("File not Found:" + filename);
            }
            currentPath.push(f);
            this.filter = filter;
            findNextFile();
        }

        /**
         * The stack always has a 'file' on the top if it has one, so return true if the stack is non empty.
         */
        @Override
        public boolean hasNext() {
            return currentPath.size() > 0;
        }

        /** {@inheritDoc} */
        @Override
        public Object next() {
            if (currentPath.size() > 0) {
                Object tmp = currentPath.pop();
                findNextFile();
                return tmp;
            }
            throw new NoSuchElementException();
            // return null;
        }

        /** Not supported, but we could delete the file in this case, or should we? */
        @Override
        public void remove() {}

        /** Finally here is all of the meat of this file. */
        private void findNextFile() {
            // Start by assumming that there are no files left.
            boolean found = false;
            /**
             * While the stack is not empty, and we have not found the type of file that we are looking for.
             */
            while (currentPath.size() > 0 && !found) {
                Object tmp = currentPath.peek();
                if (tmp instanceof File && !((File) tmp).isDirectory()) {
                    // We found one. Leave it on the stack for 'next()'
                    found = true;
                } else if (tmp instanceof File && ((File) tmp).isDirectory()) {

                    // Pop the entry for the directory name if not desired
                    Object theDir = currentPath.pop();

                    // Add the directory contents to the stack
                    File[] tmpContents = ((File) tmp).listFiles(filter);
                    currentPath.push(new DirectoryList(tmpContents));

                    // Put back the directory if we are supposed to return thm
                    if (wantDirectories) {
                        currentPath.push(theDir);
                        found = true;
                    }

                } else if (tmp instanceof DirectoryList) {
                    // This is a directories contents. Check for the next entry.
                    if (((DirectoryList) tmp).hasNext()) {
                        currentPath.push(((DirectoryList) tmp).next());
                    } else {
                        currentPath.pop();
                    }
                }
            }
        }

    }
    /**
     * Simple class to capture the contents of a directory AND a position within it. We use Iterator like methods, but avoid
     * the added complexity of the 'Iterator' class.
     */
    static class DirectoryList {
        private File[] contents;
        private int position;

        public DirectoryList(File[] contents) {
            this.contents = contents;
            position = 0;
        }

        public boolean hasNext() {
            return (position < contents.length);
        }

        public File next() {
            return contents[position++];
        }
    }
}
