package emissary.util.io;

import jakarta.annotation.Nullable;

/*
 $Id$
 */
/*
 $Id$
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

public class ReadOutput implements Runnable {

    private final BufferedReader br;
    @Nullable
    private PrintStream ps = null;
    private final String tag;
    public boolean finished = false;

    public ReadOutput(InputStream is, String str) {
        br = new BufferedReader(new InputStreamReader(is));
        tag = str;
    }

    public ReadOutput(InputStream is, String str, PrintStream outp) {
        br = new BufferedReader(new InputStreamReader(is));
        tag = str;
        ps = outp;
    }

    @Override
    public void run() {
        String aLine = "";
        finished = false;
        try {
            while ((aLine = br.readLine()) != null && !finished) {
                if (ps != null) {
                    ps.println(tag + aLine);
                }
            }
            br.close();
        } catch (IOException ignored) {
            // empty catch block
        }

    }

    public void finish() {
        finished = true;
    }
}
