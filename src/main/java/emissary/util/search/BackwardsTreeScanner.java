/*
 * BackwardsTreeScanner.java
 *
 * Created on February 20, 2002, 1:19 PM
 */

package emissary.util.search;

import java.io.PrintStream;

/**
 * This class implements a tree state machine scanner that searches text backwards starting from the end. A list of
 * strings is provided as the keywords to be searched. This class is usefull for a relatively small set of keywords.
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

    /** Original list of keywords storred in byte array form. */
    byte[][] keywords;
    /** Root node of tree state diagram. Always start a search from here! */
    State root = new State((byte) 0);

    /**
     * a tester. Run it at look at stdout. It prints a tree representation of the state diagram and then searches.
     */
    public static void main(String[] args) {
        try {
            // a list of interesting keywords. */
            String[] keys = {"\nabc", "\nxyz", "\nab", "\nXYZ", "\npqr", "\na string", "Q"};
            // A string for holding the test data to be searched
            String dataString = "";
            // The thing we are testing
            BackwardsTreeScanner scanner = new BackwardsTreeScanner(keys);
            // Make up an interesting string. The second half should never match.
            for (int i = 0; i < keys.length; i++) {
                dataString += keys[i];
            }
            // for (int i = 0;i<keys.length;i++)dataString +=keys[i].toString().substring(0,2);
            // A byte array version of the data.
            byte[] dataBytes = dataString.getBytes();

            // A vector for holding the results.
            HitList hits = new HitList();
            /*
             * loop through the data from beginint to end calling scan at each position. This shows how to use scan(),
             * but in general this should be used more effediently (with a boyer more algorithm or something.
             */
            for (int pos = 1; pos < dataBytes.length; pos++) {
                scanner.scan(dataBytes, pos, hits);
                for (int i = 0; i < hits.size(); i++) {
                    Hit tmp = hits.get(i);
                    System.out.println("Hit At:" + tmp.getOffset() + " id: " + tmp.getID());
                }
                hits.clear();
            }
        } catch (Exception e) {
            System.out.println("Exception in test:" + e);
            e.printStackTrace();
        }
    }

    public BackwardsTreeScanner(String[] keywordStrings) throws Exception {
        // make byte arrays
        keywords = new byte[keywordStrings.length][];
        // and learn them
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = keywordStrings[i].getBytes();
            root.learn(keywordStrings[i].getBytes(), i);
        }
        // root.print(System.out);
    }

    /**
     * This scans the byte array backwards from the offset. Each hit is added to the result vector. We stop when all
     * posibilities are found
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
         * Walk throught he keyword backwards. Adding states to the root (or current state) when they don't exists. At
         * the end, record the keyowrd id in the ending state.
         * 
         * Warning this is recursive, but thats OK for small keywords.
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
                for (int i = 0; i < matches.length; i++) {
                    out.print(" " + matches[i]);
                }
                out.println(" ]");
            }
            for (int i = 0; i < nextStates.length; i++) {
                if (nextStates[i] != null) {
                    nextStates[i].print(out, prefix + "  ");
                }
            }
        }
    }
}
