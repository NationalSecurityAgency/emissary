package emissary.core;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;

import com.codahale.metrics.health.HealthCheck;
import emissary.pickup.file.FilePickUpPlace;
import org.apache.commons.io.filefilter.HiddenFileFilter;

/**
 * A health check that warns if the input data queue is larger than a given threshold or if the aggregate file size is
 * larger than a given threshold. This only applies to FilePickUpPlace.
 */
public class FilePickUpPlaceHealthCheck extends HealthCheck {

    /**
     * How many files to allow in the queue before we deem the situation unhealthy
     */
    protected int maxFileCountBeforeUnhealthy = Integer.MAX_VALUE; // default is unbounded

    /**
     * How large we let the aggregate file size to get before we deem the situation unhealthy
     */
    protected long maxAggregateFileSizeBeforeUnhealthy = Long.MAX_VALUE; // default is unbounded

    public FilePickUpPlaceHealthCheck(final int maxFileCountBeforeUnhealthy, final long maxAggregateFileSizeBeforeUnhealthy) {
        this.maxFileCountBeforeUnhealthy = maxFileCountBeforeUnhealthy;
        this.maxAggregateFileSizeBeforeUnhealthy = maxAggregateFileSizeBeforeUnhealthy;
    }

    @Override
    protected Result check() throws Exception {

        int aggregateFileCount = 0; // how many things we think are in the queue
        long aggregateFileSize = 0; // aggregate file size across all files

        try {
            final FilePickUpPlace pup = (FilePickUpPlace) Namespace.lookup("FilePickUpPlace"); // if no exception, will
                                                                                               // not be
                                                                                               // null

            // Get the inputDirs by reflection, result should be a String[]
            final Field inputDirField = pup.getClass().getDeclaredField("inputDataDirs");
            inputDirField.setAccessible(true); // make it so we can read it

            if (inputDirField.getType().isArray()) {
                // This better be a String[], but we are not checking for the String part
                final String[] inputDirs = (String[]) inputDirField.get(pup);
                if (inputDirs != null) {
                    for (int dirIdx = 0; dirIdx < inputDirs.length; dirIdx++) {
                        final File inputDir = new File(inputDirs[dirIdx]);
                        if ((inputDir != null) && inputDir.isDirectory()) {
                            final File[] files = inputDir.listFiles((FileFilter) HiddenFileFilter.VISIBLE);
                            aggregateFileCount += files.length;
                            if (files != null) {
                                for (int fileIdx = 0; fileIdx < files.length; fileIdx++) {
                                    aggregateFileSize += files[fileIdx].length();
                                }
                            }
                        }
                    }
                }
            }

            // Check either condition
            if (aggregateFileCount > this.maxFileCountBeforeUnhealthy) {
                return Result.unhealthy("Large number of files backed up for FilePickUpPlace = " + aggregateFileCount);
            } else if (aggregateFileSize > this.maxAggregateFileSizeBeforeUnhealthy) {
                return Result.unhealthy("Large aggregate file size backed up for FilePickUpPlace = " + aggregateFileSize);
            }

        } catch (NamespaceException ne) {
            // This gets throw if can't find FilePickUpPlace, assume it is not configured
            // and things are healthy
        } catch (NoSuchFieldException nsfe) {
            // Possibly thrown by getDeclaredField, assume FilePickUpPlace implementation has changed
        } catch (SecurityException se) {
            // Possibly thrown by getDeclaredField - would prevent our access to the field
        } catch (NullPointerException npe) {
            // A variety of methods throw NPEs
        } catch (IllegalArgumentException iae) {
            // Thrown by get()
        } catch (IllegalAccessException iae) {
            // Also thrown by get()
        } catch (ExceptionInInitializerError eiie) {
            // Also thrown by get()
        }

        // If we get here, we assume things are OK
        return Result.healthy("FilePickUpPlace appears to be healthy");
    }


}
