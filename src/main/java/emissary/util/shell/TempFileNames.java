package emissary.util.shell;

import emissary.util.io.FileManipulator;

import java.io.File;

/**
 * A related set file and directory path names suitable for operations that require temporary disk-backed files. The
 * names do not directly imply that corresponding files and directories actually exist; I/O operations including
 * creating or deletions of files or directories is the responsibility of TempFileNames instance consumers.
 */
public class TempFileNames {
    private final String tempDir;
    private final String base;
    private final String basePath;
    private final String in;
    private final String out;
    private final String inputFilename;
    private final String outputFilename;

    /**
     * Creates a new TempFileNames instance, using the same logic as the legacy{@link Executrix#makeTempFilenames()} API
     *
     * @param tmpDir configured temp directory for the server process
     * @param placeName place for which the names are needed
     * @param inFileEnding input file ending
     * @param outFileEnding output file ending
     */
    TempFileNames(String tmpDir, String placeName, String inFileEnding, String outFileEnding) {
        base = Long.toString(System.nanoTime());
        tempDir = FileManipulator.mkTempFile(tmpDir, placeName);
        in = base + inFileEnding;
        out = base + outFileEnding;
        basePath = tempDir + File.separator + base;
        inputFilename = basePath + inFileEnding;
        outputFilename = basePath + outFileEnding;
    }

    /**
     * Temporary directory name for commands that use file i/o
     * <p>
     * Corresponds to the {@link Executrix#DIR} usage in the legacy Executrix API
     * </p>
     * 
     * @return temp directory name
     */
    public String getTempDir() {
        return tempDir;
    }

    /**
     * Pseudorandom value intended to help ensure unique temporary filenames
     * <p>
     * Corresponds to the {@link Executrix#BASE} usage in the legacy Executrix API
     * </p>
     * 
     * @return Pseudorandom key for this instance
     * @deprecated this property should be considered internal-use only and will likely be removed
     */
    @Deprecated
    public String getBase() {
        return base;
    }

    /**
     * Pseudorandom sub-path intended to help ensure unique temporary filenames
     * <p>
     * Corresponds to the {@link Executrix#BASE_PATH} usage in the legacy Executrix API
     * </p>
     * 
     * @return Pseudorandom sub-path
     * @deprecated this property should be considered internal-use only and will likely be removed
     */
    @Deprecated
    public String getBasePath() {
        return basePath;
    }

    /**
     * Pseudo-random value intended to help ensure unique input filenames
     * <p>
     * Corresponds to the {@link Executrix#IN} usage in the legacy Executrix API
     * </p>
     * 
     * @return Input filename path, relative to the {@link #getTempDir()} value
     */
    public String getIn() {
        return in;
    }

    /**
     * Pseudo-random value intended to help ensure unique output filenames
     * <p>
     * Corresponds to the {@link Executrix#OUT} usage in the legacy Executrix API
     * </p>
     * 
     * @return Output filename path, relative to the {@link #getTempDir()} value
     */
    public String getOut() {
        return out;
    }

    /**
     * Input filename for commands that use file input
     * <p>
     * Corresponds to the {@link Executrix#INPATH} usage in the legacy Executrix API
     * </p>
     * 
     * @return input filename
     */
    public String getInputFilename() {
        return inputFilename;
    }

    /**
     * Output filename for commands that generate file output
     * <p>
     * Corresponds to the {@link Executrix#OUTPATH} usage in the legacy Executrix API
     * </p>
     * 
     * @return Output filename
     */
    public String getOutputFilename() {
        return outputFilename;
    }

    @Override
    public String toString() {
        return "TempFileNames{" +
                "tempDir='" + tempDir + '\'' +
                ", base='" + base + '\'' +
                ", basePath='" + basePath + '\'' +
                ", in='" + in + '\'' +
                ", out='" + out + '\'' +
                ", inputFilename='" + inputFilename + '\'' +
                ", outputFilename='" + outputFilename + '\'' +
                '}';
    }
}
