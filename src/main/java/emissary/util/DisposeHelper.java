package emissary.util;

import emissary.core.IBaseDataObject;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper methods to ease handling of Dispose objects
 * 
 * Dispose objects are added to BDOs to handle clean-up after a
 * {@link emissary.core.channels.SeekableByteChannelFactory} is finished with
 */
public final class DisposeHelper {
    private DisposeHelper() {}

    /** Metadata key to use to store runnables in on a BDO. */
    public static final String KEY = "DISPOSE_RUNNABLES";
    private static final String VALIDATION_MSG_IBDO = "Cannot be null: ibdo";
    private static final String VALIDATION_MSG_RUNNABLE = "Cannot be null: Runnable";

    private static final Logger LOGGER = LoggerFactory.getLogger(DisposeHelper.class);

    /**
     * Add a Dispose object to a BDO
     * 
     * @param ibdo to add the Runnable to
     * @param newRunnable to handle disposing of the referenced object
     */
    public static void add(final IBaseDataObject ibdo, final Runnable newRunnable) {
        Validate.notNull(ibdo, VALIDATION_MSG_IBDO);
        Validate.notNull(newRunnable, VALIDATION_MSG_RUNNABLE);
        if (ibdo.hasParameter(KEY)) {
            final List<Object> existingRunnables = new ArrayList<>(ibdo.getParameter(KEY));
            existingRunnables.add(newRunnable);
            ibdo.putParameter(KEY, existingRunnables);
        } else {
            ibdo.putParameter(KEY, newRunnable);
        }
    }

    /**
     * Add a list of Dispose objects to a BDO (additive to existing Dispose objects)
     * 
     * @param ibdo to add the Runnables to
     * @param newRunnables list of Runnables to add
     */
    public static void add(final IBaseDataObject ibdo, final List<Runnable> newRunnables) {
        Validate.notNull(ibdo, VALIDATION_MSG_IBDO);
        Validate.notNull(newRunnables, VALIDATION_MSG_RUNNABLE);
        if (ibdo.hasParameter(KEY)) {
            final List<Object> existingRunnables = new ArrayList<>(ibdo.getParameter(KEY));
            existingRunnables.addAll(newRunnables);
            ibdo.putParameter(KEY, existingRunnables);
        } else {
            ibdo.putParameter(KEY, newRunnables);
        }
    }

    /**
     * Set a single Dispose object to a BDO (overwrites any existing Dispose objects)
     * 
     * @param ibdo to add the Runnable to
     * @param newRunnable to handle disposing of the referenced object
     */
    public static void set(final IBaseDataObject ibdo, final Runnable newRunnable) {
        Validate.notNull(ibdo, VALIDATION_MSG_IBDO);
        Validate.notNull(newRunnable, VALIDATION_MSG_RUNNABLE);
        ibdo.setParameter(KEY, newRunnable);
    }

    /**
     * Get the list of Runnables for an object. If an object provided by the key is not a valid Runnable, it will be
     * ignored.
     * 
     * @param ibdo to get Runnables from
     * @return the list of Runnables
     */
    public static List<Runnable> get(final IBaseDataObject ibdo) {
        Validate.notNull(ibdo, VALIDATION_MSG_IBDO);
        if (!ibdo.hasParameter(KEY)) {
            return Collections.emptyList();
        }

        final List<Runnable> validatedAsRunnables = new ArrayList<>();
        final List<Object> existingRunnables = ibdo.getParameter(KEY);

        for (final Object possibleRunnable : existingRunnables) {
            if (possibleRunnable instanceof Runnable) {
                validatedAsRunnables.add((Runnable) possibleRunnable);
            } else if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Not a valid Runnable on object {}", ibdo.shortName());
            }
        }
        return validatedAsRunnables;
    }

    /**
     * Execute Runnables on provided objects. Execution will be for each object in the order provided.
     * 
     * @param ibdos to execute runnables from
     */
    public static void execute(final List<IBaseDataObject> ibdos) {
        Validate.notNull(ibdos, "Cannot be null: %s", "ibdos");
        ibdos.forEach(DisposeHelper::execute);
    }

    /**
     * Execute Runnables for the specified object.
     * 
     * @param ibdo to execute Runnables on
     */
    public static void execute(final IBaseDataObject ibdo) {
        Validate.notNull(ibdo, VALIDATION_MSG_IBDO);
        // Can't be refactored to method::reference as we need to ensure exceptions are not swallowed
        for (final Runnable runnable : DisposeHelper.get(ibdo)) {
            try {
                runnable.run();
            } catch (final Exception e) {
                LOGGER.warn("Exception while executing Runnable for {}", ibdo.shortName(), e);
            }
        }
    }
}
