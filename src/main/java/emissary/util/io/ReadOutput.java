package emissary.util.io;

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

    private BufferedReader br;
    private PrintStream ps = null;
    private String tag;
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
        } catch (IOException iox) {
            // empty catch block
        }

    }

    public void finish() {
        finished = true;
    }
}
