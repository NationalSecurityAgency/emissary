/*
 * ReadOutputLog.java
 *
 * Created on November 19, 2001, 10:37 AM
 */

package emissary.util.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ReadOutputLogger extends ProcessReader {

    public BufferedReader bufferedReader;
    public InputStreamReader inputReader;
    public boolean finished = false;
    public String name;

    public ReadOutputLogger(final String name, final InputStream in) {
        this.inputReader = new InputStreamReader(in);
        this.bufferedReader = new BufferedReader(this.inputReader);
        this.finished = false;
        this.name = name;
        if (this.name == null) {
            this.name = "";
        }
    }

    @Override
    void runImpl() {
        this.finished = false;
        try {
            String aLine;
            do {
                aLine = this.bufferedReader.readLine();
                if ((aLine != null) && !aLine.isEmpty()) {
                    // logger.info("{}:{}", this.name, aLine.replace('\n', '~'));
                }
            } while (aLine != null && !this.finished);
        } catch (IOException ignored) {
            // ignore
        }
        try {
            this.inputReader.close();
            this.bufferedReader.close();
        } catch (IOException ignored) {
            // ignore
        }
    }

    @Override
    @SuppressWarnings("Interruption")
    public void finish() {
        this.finished = true;
        this.interrupt();
    }
}
