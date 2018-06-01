package emissary.pickup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import emissary.pickup.file.FilePickUpClient;

public class BreakableFilePickUpClient extends FilePickUpClient {

    boolean actBrokenDuringReceive = false;
    boolean actBrokenDuringProcessing = false;
    boolean brokenShutdownTrigger = false;

    public BreakableFilePickUpClient(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
    }

    public BreakableFilePickUpClient(InputStream configInfo, String dir, String placeLoc) throws IOException {
        super((String) null, dir, placeLoc);
    }

    public void setBrokenDuringReceive(boolean value) {
        actBrokenDuringReceive = value;
    }

    public void setBrokenDuringProcessing(boolean value) {
        actBrokenDuringProcessing = value;
        if (value == true) {
            Timer t = new Timer("BreakableFilePickupClientMonitor", true);
            t.schedule(new TimeToActBrokenTask(), new Date(System.currentTimeMillis()), 10L);

        }
    }

    @Override
    public boolean enque(WorkBundle paths) {

        // We don't want to be given work bundles with files that
        // don't exist, so check them here. We can assert here but it
        // does not cause the test to fail directly
        for (String fname : paths.getFileNameList()) {
            File f = new File(fname);
            logger.info("Received " + paths.getBundleId() + " - " + fname + " - " + (f.exists() ? Long.valueOf(f.length()) : "missing"));
        }

        if (actBrokenDuringReceive) {
            logger.debug("Simulating broken file pickup client, shutting down now");
            this.shutDown();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
                // empty catch block
            }
            return true;
        }

        if (actBrokenDuringProcessing) {
            logger.debug("Simulating broken processing of files, shutting down after receive using eatPrefix " + paths.getEatPrefix());
            for (String incomingName : paths.getFileNameList()) {
                File incoming = new File(incomingName);
                File toProcess = getInProcessFileNameFor(incoming, paths.getEatPrefix());
                File holdFile = findFileInHoldingArea(incoming, paths.getEatPrefix());
                if (holdFile == null) {
                    if (!renameToInProcessAreaAs(incoming, toProcess)) {
                        logger.error("Cannot rename incoming " + incoming + " to holding area " + toProcess + " while trying to act broken");
                    }
                } else {
                    logger.info("File found in holding area " + holdFile + " so not renaming");
                }
            }
            brokenShutdownTrigger = true;
            return false;
        }

        // normal processing
        super.enque(paths);
        return true;
    }

    class TimeToActBrokenTask extends TimerTask {
        @Override
        public void run() {
            if (brokenShutdownTrigger == true) {
                shutDown();
                brokenShutdownTrigger = false;
            }
        }
    }
}
