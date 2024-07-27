package emissary.core;

import emissary.directory.KeyManipulator;
import emissary.place.IServiceProviderPlace;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class TransformHistory implements Serializable {

    private static final long serialVersionUID = -7252497842562281631L;

    private final List<History> history;

    public TransformHistory() {
        history = new ArrayList<>();
    }

    public TransformHistory(TransformHistory history) {
        this.history = new ArrayList<>(history.history);
    }

    /**
     * Replace history with the new history
     *
     * @param keys of new history strings to use
     */
    public void set(List<String> keys) {
        clear();
        addAll(keys.stream().map(History::new).collect(Collectors.toList()));
    }

    /**
     * Replace history with the new history
     *
     * @param history of new history strings to use
     */
    public void set(TransformHistory history) {
        clear();
        addAll(history.history);
    }

    private void addAll(List<History> history) {
        this.history.addAll(history);
    }

    /**
     * Appends the new key to the transform history. This is called by MobileAgent before moving to the new place. It
     * usually adds the four-tuple of a place's key
     *
     * @see emissary.core.MobileAgent#agentControl
     * @param key the new value to append
     */
    public void append(final String key) {
        append(key, false);
    }

    /**
     * Appends the new key to the transform history. This is called by MobileAgent before moving to the new place. It
     * usually adds the four-tuple of a place's key. Coordinated history keys are meant for informational purposes and have
     * no bearing on the routing algorithm. It is important to list the places visited in coordination, but should not
     * report as the last place visited.
     *
     * @see emissary.core.MobileAgent#agentControl
     * @param key the new value to append
     * @param coordinated true if history entry is for informational purposes only
     */
    public void append(String key, boolean coordinated) {
        if (coordinated) {
            if (CollectionUtils.isNotEmpty(history)) {
                history.get(history.size() - 1).addCoordinated(key);
            }
        } else {
            history.add(new History(key));
        }

    }

    /**
     * Clear the transformation history
     */
    public void clear() {
        history.clear();
    }

    /**
     * For backwards compatibility, only return the history that does not contain the coordinated places
     *
     * @return List of places visited sans coordination places
     */
    public List<String> get() {
        return get(false);
    }

    /**
     * Return the transform history, optionally including coordinated places
     *
     * @return List of places visited
     */
    public List<String> get(boolean includeCoordinated) {
        if (includeCoordinated) {
            List<String> keys = new ArrayList<>();
            history.forEach(k -> {
                keys.add(k.getKey());
                keys.addAll(k.getCoordinated());
            });
            return keys;
        } else {
            return history.stream()
                    .map(History::getKey)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Return the full history object
     *
     * @return history object
     */
    public List<History> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /**
     * Get the last place visited (does not include places visited during coordination)
     *
     * @return last place visited
     */
    @Nullable
    public String lastVisit() {
        if (CollectionUtils.isEmpty(history)) {
            return null;
        }
        return history.get(history.size() - 1).getKey();
    }

    /**
     * Get the second-to-last place visited (does not include places visited during coordination)
     *
     * @return second-to-last place visited
     */
    @Nullable
    public String penultimateVisit() {
        if (CollectionUtils.isEmpty(history) || history.size() < 2) {
            return null;
        }
        return history.get(history.size() - 2).getKey();
    }

    /**
     * Test to see if a place has been visited (does not include places visited during coordination)
     *
     * @return true is place has been visited
     */
    public boolean hasVisited(final String pattern) {
        for (final String historyValue : get()) {
            if (KeyManipulator.gmatch(historyValue, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this payload hasn't had any processing yet. Does not count parent processing as being for this payload.
     *
     * @return true if not yet started
     */
    public boolean beforeStart() {
        List<String> historyList = get();
        if (historyList.isEmpty()) {
            return true;
        }
        final String s = historyList.get(historyList.size() - 1);
        return s.contains(IServiceProviderPlace.SPROUT_KEY);
    }

    @Override
    public String toString() {
        final StringBuilder myOutput = new StringBuilder();
        final String ls = System.lineSeparator();
        myOutput.append("transform history (").append(this.history.size()).append(") :").append(ls);
        history.forEach(x -> myOutput.append(x.toString()).append(ls));
        return myOutput.toString();
    }

    public static class History {
        String key;
        List<String> coordinated = new ArrayList<>();

        /**
         * Needed to support Kryo deserialization
         */
        private History() {}

        public History(String key) {
            this.key = key;
        }

        public String getKey() {
            return getKey(false);
        }

        public String getKey(boolean stripUrl) {
            return stripUrl ? stripUrl(key) : key;
        }

        public List<String> getCoordinated() {
            return getCoordinated(false);
        }

        public List<String> getCoordinated(boolean stripUrl) {
            return stripUrl ? coordinated.stream().map(History::stripUrl).collect(Collectors.toUnmodifiableList())
                    : Collections.unmodifiableList(coordinated);
        }

        public void addCoordinated(String key) {
            coordinated.add(key);
        }

        protected static String stripUrl(String key) {
            return StringUtils.substringBefore(key, ".http");
        }

        @Override
        public String toString() {
            StringBuilder hist = new StringBuilder("        -> " + getKey());
            for (String coord : coordinated) {
                hist.append(System.lineSeparator()).append("           +--> ").append(coord);
            }
            return hist.toString();
        }
    }
}
