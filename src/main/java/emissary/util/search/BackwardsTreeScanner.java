/*
 * BackwardsTreeScanner.java
 *
 * Created on February 20, 2002, 1:19 PM
 */

package emissary.util.search;

import java.io.PrintStream;

/**
 * This class implements a tree state machine scanner that searches text backwards starting from the end. A list of
 * strings is provided as the keywords to be searched. This class is useful for a relatively small set of keywords.
 * Larger keyword lists can be used if you have memory!
 *
 * @author ce
 * @version 1.0
 */
public class BackwardsTreeScanner {

    // The internal structure of the offset-keyword id array
    // contained in the hit vectors
    // public static final int OFFSET = 0;
    // public static final int ID = 1;

    /** Root node of tree state diagram. Always start a search from here! */
    private State root = new State((byte) 0);

    /**
     * Optional empty constructor
     */
    public BackwardsTreeScanner() {

    }

    /**
     * Constructor that delegates learning keywords to resetKeywords method.
     *
     * @param keywordStrings - array of keywords to learn
     * @throws Exception - thrown if problem encountered
     */
    public BackwardsTreeScanner(String[] keywordStrings) throws Exception {
        resetKeywords(keywordStrings);
    }

    /**
     * Resets keywords and internal State learns them. This method destroys previous state.
     *
     * @param keywordStrings - String of keywords to learn
     * @throws Exception - if problem encountered while learning
     */
    public synchronized void resetKeywords(String[] keywordStrings) throws Exception {
        // make byte arrays
        /** Original list of keywords stored in byte array form. */
        byte[][] keywords = new byte[keywordStrings.length][];
        root = new State((byte) 0); // reset state
        // and learn them
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = keywordStrings[i].getBytes();
            root.learn(keywordStrings[i].getBytes(), i);
        }
        // root.print(System.out);
    }

    /**
     * This scans the byte array backwards from the offset. Each hit is added to the result vector. We stop when all
     * possibilities are found
     */
    public synchronized int scan(byte[] data, int offset, HitList result) throws Exception {
        if (result == null) {
            throw new Exception("Null result vector in 3rd parameter of scan()");
        }
        // reset the state machine
        State state = root;
        int curPos = offset;
        while (state != null && curPos >= 0) {
            // Save any matches for this state.
            // get the next character
            byte ch = data[curPos];
            // move to the next state. Really complicated, right?
            if (ch < 0) {
                state = state.nextStates[256 + (int) ch];
            } else {
                state = state.nextStates[ch];
            }
            // move the the previous character
            if (state != null && state.matches != null) {
                for (int i = 0; i < state.matches.length; i++) {
                    int id = state.matches[i];
                    Hit tmp = new Hit(curPos, id);
                    result.add(tmp);
                }
            }
            curPos--;
        }
        return curPos;
    }

    /*
     * This class implements a state machine that can learn character sequences.
     */
    public class State {
        // Each state has 256 transitions leaving it to new states based
        // on a single ascii character. If there is no next state for a
        // character, then the next state will be null.
        public State[] nextStates = new State[256];
        // Each state can be visited by a single character. This is it!
        public byte gotHereBy;
        // A list of keyword ids that are matched at this state.
        public int[] matches = null;

        // constructor
        public State(byte gotHereBy) {
            this.gotHereBy = gotHereBy;
        }

        public void learn(byte[] word, int id) throws Exception {
            learn(word, word.length - 1, id);
        }

        /**
         * Walk through the keyword backwards. Adding states to the root (or current state) when they don't exists. At the end,
         * record the keyword id in the ending state.
         * 
         * Warning this is recursive, but that is OK for small keywords.
         */
        public void learn(byte[] word, int wordLoc, int id) throws Exception {
            if (word == null) {
                throw new Exception("null keyword in BackwardsTreeScanner.learn()");
            }
            if (wordLoc >= word.length) {
                throw new Exception("char pos > word length:" + wordLoc + ">" + word.length);
            }
            if (wordLoc < 0) {
                // we are finished because this is the first character,
                // so save the id in this state. We want the matches to be
                // in an array so this is a little harder than a vector thing.
                if (matches == null) {
                    matches = new int[0];
                }
                int[] newMatches = new int[matches.length + 1];
                System.arraycopy(matches, 0, newMatches, 0, matches.length);
                matches = newMatches;
                matches[matches.length - 1] = id;
            } else {
                // Get the next character in the word
                byte nextChar = word[wordLoc];
                // See if the state already exists
                State nextState = nextStates[nextChar];
                if (nextState == null) {
                    // Make a new state because it isn't there yet.
                    nextState = nextStates[nextChar] = new State(nextChar);
                }
                // Learn the rest of the keyword in the new state.
                nextState.learn(word, wordLoc - 1, id);
            }
        }

        public void print(PrintStream out) {
            print(out, "root:");
        }

        // Make a pretty picture.
        public void print(PrintStream out, String prefix) {
            if (gotHereBy < ' ' || gotHereBy > '~') {
                out.println(prefix + "-> " + "(byte)" + gotHereBy);
            } else {
                out.println(prefix + "-> " + (char) gotHereBy);
            }
            if (matches != null) {
                out.print(prefix + "ids [");
                for (int match : matches) {
                    out.print(" " + match);
                }
                out.println(" ]");
            }
            for (State nextState : nextStates) {
                if (nextState != null) {
                    nextState.print(out, prefix + "  ");
                }
            }
        }
    }
}
