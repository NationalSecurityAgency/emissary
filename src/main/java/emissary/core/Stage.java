package emissary.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Information class about processing stages in the workflow
 */
public class Stage {
    // List of stages to use. Use non-default constructors or extend class to change
    protected static List<String> stages;

    // Each stage must be marked parallel or not
    protected static List<Boolean> parallel;

    public Stage() {}

    static {
        stages = new ArrayList<String>();
        parallel = new ArrayList<Boolean>();
        stages.add("STUDY"); // prepare, coordinate idents
        parallel.add(Boolean.FALSE);

        stages.add("ID"); // identification phase
        parallel.add(Boolean.FALSE);

        stages.add("COORDINATE"); // Coordinate processing
        parallel.add(Boolean.FALSE);

        stages.add("PRETRANSFORM"); // before transform hook
        parallel.add(Boolean.TRUE);

        stages.add("TRANSFORM"); // transformation phase
        parallel.add(Boolean.FALSE);

        stages.add("POSTTRANSFORM"); // after transform hook
        parallel.add(Boolean.TRUE);

        stages.add("ANALYZE"); // analysis/metadata generation
        parallel.add(Boolean.TRUE);

        stages.add("VERIFY"); // verify for output
        parallel.add(Boolean.FALSE);

        stages.add("IO"); // output
        parallel.add(Boolean.FALSE);

        stages.add("REVIEW"); // finish off
        parallel.add(Boolean.FALSE);
    }


    /**
     * Indicate if a stage supports parallel processing or not
     * 
     * @param i the index of the stage
     */
    public boolean isParallelStage(final int i) {
        if (i >= 0 && i < parallel.size()) {
            return parallel.get(i).booleanValue();
        }
        return false;
    }

    /**
     * Indicate if named stages supports parallel processing or not
     * 
     * @param stage the name of the stage
     */
    public boolean isParallelStage(final String stage) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).equals(stage)) {
                return parallel.get(i).booleanValue();
            }
        }
        return false;
    }


    /**
     * Return stage name at index
     */
    public String getStageName(final int i) {
        if (i >= 0 && i < stages.size()) {
            return stages.get(i);
        } else {
            return "UNDEFINED";
        }
    }

    /**
     * Return stage index for name
     * 
     * @param stage the name of the stage
     * @return the index of the stage or -1 if no such stage
     */
    public int getStageIndex(final String stage) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).equals(stage)) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Return a list of stages
     */
    public List<String> getStages() {
        return new ArrayList<String>(stages);
    }

    /**
     * Return the stage following the named stage or null if the end of the list
     * 
     * @param stage the one to find following for
     */
    public String nextStageAfter(final String stage) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).equals(stage)) {
                if (i < stages.size() - 1) {
                    return stages.get(i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Get size of stage list
     */
    public int size() {
        return stages.size();
    }
}
