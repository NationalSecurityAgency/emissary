package emissary.util.search;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FastBoyerMoore {
    public byte[][] keywords;
    int minKeywordLength;
    int[] lookup = new int[259];
    transient BackwardsTreeScanner scanner;
    byte[] data = null;
    BackwardsTreeScanner.State root;

    // copy constructor

    private FastBoyerMoore() {}

    public FastBoyerMoore(final FastBoyerMoore original) {
        this();
        this.keywords = original.keywords;
        this.minKeywordLength = original.minKeywordLength;
        this.lookup = original.lookup;
        this.root = original.root;
        this.scanner = new BackwardsTreeScanner(original.scanner);
    }

    public FastBoyerMoore(final String[] keywordStrings) throws Exception {
        this.keywords = new byte[keywordStrings.length][];
        this.minKeywordLength = Integer.MAX_VALUE;
        for (int i = 0; i < this.keywords.length; i++) {
            this.keywords[i] = keywordStrings[i].getBytes();
            this.minKeywordLength = Math.min(this.minKeywordLength, this.keywords[i].length);
        }
        for (int i = 0; i < this.lookup.length; i++) {
            this.lookup[i] = this.minKeywordLength;
        }
        // each keyword
        for (int i = 0; i < this.keywords.length; i++) {
            final byte[] kw = this.keywords[i];
            // each keyword character
            for (int j = 0; j < kw.length - 1; j++) {
                this.lookup[kw[j]] = Math.min(this.lookup[kw[j]], kw.length - j - 1);
            }
        }
        this.data = null;
        this.scanner = new BackwardsTreeScanner(keywordStrings);
        this.root = this.scanner.getRoot();
    }

    public FastBoyerMoore(final String[][] keywordStrings) throws Exception {
        this.minKeywordLength = Integer.MAX_VALUE;
        for (int i = 0; i < keywordStrings.length; i++) {
            for (int j = 0; j < keywordStrings[i].length; j++) {
                this.minKeywordLength = Math.min(this.minKeywordLength, keywordStrings[i][j].length());
            }
        }
        for (int i = 0; i < this.lookup.length; i++) {
            this.lookup[i] = this.minKeywordLength;
        }
        // each keyword
        for (int i = 0; i < keywordStrings.length; i++) {
            for (int j = 0; j < keywordStrings[i].length; j++) {
                final byte[] kw = keywordStrings[i][j].getBytes();
                // each keyword character
                for (int k = 0; k < kw.length - 1; k++) {
                    this.lookup[kw[k]] = Math.min(this.lookup[kw[k]], kw.length - k - 1);
                }
            }
        }
        this.data = null;
        this.scanner = new BackwardsTreeScanner(keywordStrings);
        this.root = this.scanner.getRoot();
    }

    public void setData(final byte[] dataArg) {
        this.scanner.setData(dataArg);
        this.data = dataArg;
    }

    public void scan(final byte[] dataArg, final int start, final int end, final Collection<int[]> result) {
        this.data = dataArg;
        this.scanner.setData(dataArg);
        scan(start, end, result);
    }

    public void scan(final int start, final int end, final Collection<int[]> result) {
        final int actualEnd = Math.min(end, this.data.length);
        int pos = start;
        while (pos < actualEnd) {
            final int ch = this.data[pos] & 0x7f;
            final int jump = this.lookup[ch];
            BackwardsTreeScanner.State state = this.root.nextStates[ch];
            int curPos = pos - 1;
            while ((state != null) && (curPos >= 0)) {
                if (state.matches != null) {
                    for (int i = 0; i < state.matches.length; i++) {
                        final int id = state.matches[i];
                        final int[] tmp = new int[3];
                        tmp[0] = curPos + 1;
                        tmp[1] = id;
                        tmp[2] = pos - curPos;
                        result.add(tmp);
                    }
                }
                final int ch2 = this.data[curPos] & 0x7f;
                state = state.nextStates[ch2];
                curPos--;
            }
            if ((state != null) && (curPos == -1)) {
                for (int i = 0; i < state.matches.length; i++) {
                    final int id = state.matches[i];
                    final int[] tmp = new int[3];
                    tmp[0] = curPos + 1;
                    tmp[1] = id;
                    tmp[2] = pos - curPos;
                    result.add(tmp);
                }
            }
            pos += jump;
        }
    }

    public int staticSingleScan(final byte[] dataArg, final int start, final int end, final Collection<int[]> result) {
        final int actualEnd = Math.min(end, dataArg.length);
        boolean found = false;
        int pos = start;
        while ((pos < actualEnd) && !found) {
            final int ch = dataArg[pos] & 0x7f;
            final int jump = this.lookup[ch];
            BackwardsTreeScanner.State state = this.root.nextStates[ch];
            int curPos = pos - 1;
            while ((state != null) && (curPos >= 0)) {
                if (state.matches != null) {
                    for (int i = 0; i < state.matches.length; i++) {
                        final int id = state.matches[i];
                        final int[] tmp = new int[3];
                        tmp[0] = curPos + 1;
                        tmp[1] = id;
                        tmp[2] = pos - curPos;
                        result.add(tmp);
                        found = true;
                    }
                }
                final int ch2 = dataArg[curPos] & 0x7f;
                state = state.nextStates[ch2];
                curPos--;
            }
            if ((state != null) && (state.matches != null) && (curPos == -1)) {
                for (int i = 0; i < state.matches.length; i++) {
                    final int id = state.matches[i];
                    final int[] tmp = new int[3];
                    tmp[0] = curPos + 1;
                    tmp[1] = id;
                    tmp[2] = pos - curPos;
                    result.add(tmp);
                    found = true;
                }
            }
            pos += jump;
        }
        return pos;
    }

    public static final int ID = 1;
    public static final int LOC = 0;
    public static final int LENGTH = 2;

    public static void main(final String[] args) {
        try {
            // a list of interesting keywords. */
            final String[][] keys = {{"\nABCD"}, // 0,1,2,3,4
                    {"\nABC"}, // 5 6 7 8
                    {"\nAB"}, // 9 10 11
                    {"\n//xyz//"}, // 12 13 14 15 16 17 18 19
                    {"\nxxxxx"}, // 20 21 22 23 24 25
                    {"\nabcdefghi"}, // 26 27 28 29 30 31 32 33 34 35
                    {"\nabcde", "\nabcdefg"}}; // 36 37 38 39 41 41 42 43 44 45
            // A string for holding the test data to be searched
            final StringBuilder dataString = new StringBuilder();
            // The thing we are testing
            final FastBoyerMoore scanner = new FastBoyerMoore(keys);
            // Make up an interesting string. The second half should never match.
            for (int i = 0; i < keys.length; i++) {
                for (int j = 0; j < keys[i].length; j++) {
                    dataString.append(keys[i][j]);
                }
            }
            // for (int i = 0;i<keys.length;i++)dataString +=keys[i].toString().substring(0,2);
            // A byte array version of the data.
            final byte[] dataBytes = dataString.toString().getBytes();

            // A vector for holding the results.
            final List<int[]> result = new ArrayList<int[]>();
            scanner.setData(dataBytes);
            scanner.scan(0, dataBytes.length, result);
            for (int i = 0; i < result.size(); i++) {
                final int[] tmp = result.get(i);
                System.out.println("Hit At:" + tmp[0] + " id: " + tmp[1] + " l: " + tmp[2]);
            }
        } catch (Exception e) {
            System.out.println("Exception in test:" + e);
            e.printStackTrace();
        }
    }

    /**
     * This class implements a tree state machine scanner that searches text backwards starting from the end. A list of
     * strings is provided as the keywords to be searched. This class is usefull for a relatively small set of keywords.
     * Larger keyword lists can be used if you have memory!
     *
     * @author ce
     * @version 1.0
     */
    public static class BackwardsTreeScanner {
        /** Original list of keywords storred in byte array form. */
        // byte[][] keywords;
        /** Root node of tree state diagram. Always start a search from here! */
        State root = new State((byte) 0);
        byte[] data = null;

        public BackwardsTreeScanner(final BackwardsTreeScanner o) {
            this.root = o.root;
        }

        public static void main(final String[] args) {
            try {
                // a list of interesting keywords. */
                final String[][] keys = {{"\nABCD", "\nABC", "\nAB", "\n//xyz//", "\nxxxxx", "\nabcdefghi"}, {"\nabcde", "\nabcdefg"}};

                // The thing we are testing
                final BackwardsTreeScanner scanner = new BackwardsTreeScanner(keys);
                // A string for holding the test data to be searched
                final StringBuilder dataString = new StringBuilder();
                // Make up an interesting string. The second half should never match.
                for (int i = 0; i < keys.length; i++) {
                    for (int j = 0; j < keys[i].length; j++) {
                        dataString.append(keys[i][j]);
                    }
                }
                // for (int i = 0;i<keys.length;i++)dataString +=keys[i].toString().substring(0,2);
                // A byte array version of the data.
                final byte[] dataBytes = dataString.toString().getBytes();

                // A vector for holding the results.
                final List<int[]> hits = new ArrayList<int[]>();
                /*
                 * loop through the data from beginint to end calling scan at each position. This shows how to use scan(), but in
                 * general this should be used more effediently (with a boyer more algorithm or something.
                 */
                for (int pos = 1; pos < dataBytes.length; pos++) {
                    scanner.scan(dataBytes, pos, hits);
                    for (int i = 0; i < hits.size(); i++) {
                        final int[] tmp = hits.get(i);
                        System.out.println("Hit At:" + tmp[0] + " id: " + tmp[1] + " l: " + tmp[2]);
                    }
                    hits.clear();
                }
            } catch (Exception e) {
                System.out.println("Exception in test:" + e);
                e.printStackTrace();
            }
        }

        public static void main2(final String[] args) {
            try {
                // a list of interesting keywords. */
                final String[] keys = {"\nABCD", "\nABC", "\nAB", "\n//xyz//", "\nxxxxx", "\nabcdefghi", "\nabcde", "\nabcdefg"};
                // A string for holding the test data to be searched
                final StringBuilder dataString = new StringBuilder();
                // The thing we are testing
                final BackwardsTreeScanner scanner = new BackwardsTreeScanner(keys);
                // Make up an interesting string. The second half should never match.
                for (int i = 0; i < keys.length; i++) {
                    dataString.append(keys[i]);
                }
                // for (int i = 0;i<keys.length;i++)dataString +=keys[i].toString().substring(0,2);
                // A byte array version of the data.
                final byte[] dataBytes = dataString.toString().getBytes();

                // A vector for holding the results.
                final List<int[]> hits = new ArrayList<int[]>();
                /*
                 * loop through the data from beginint to end calling scan at each position. This shows how to use scan(), but in
                 * general this should be used more effediently (with a boyer more algorithm or something.
                 */
                for (int pos = 1; pos < dataBytes.length; pos++) {
                    scanner.scan(dataBytes, pos, hits);
                    for (int i = 0; i < hits.size(); i++) {
                        final int[] tmp = hits.get(i);
                        System.out.println("Hit At:" + tmp[0] + " id: " + tmp[1]);
                    }
                    hits.clear();
                }
            } catch (Exception e) {
                System.out.println("Exception in test:" + e);
                e.printStackTrace();
            }
        }

        public BackwardsTreeScanner(final String[][] keywordStrings) throws Exception {
            for (int i = 0; i < keywordStrings.length; i++) {
                for (int j = 0; j < keywordStrings[i].length; j++) {
                    this.root.learn(keywordStrings[i][j].getBytes(), i);
                }
            }
            // this.root.print(System.out);
        }

        public BackwardsTreeScanner(final String[] keywordStrings) throws Exception {
            // make byte arrays
            final byte[][] keywords = new byte[keywordStrings.length][];
            // and learn them
            for (int i = 0; i < keywords.length; i++) {
                keywords[i] = keywordStrings[i].getBytes();
                this.root.learn(keywordStrings[i].getBytes(), i);
            }
            // this.root.print(System.out);
        }

        public synchronized State getRoot() {
            return this.root;
        }

        public synchronized void setData(final byte[] dataArg) {
            this.data = dataArg;
        }

        /**
         * This scans the byte array backwards from the offset. Each hit is added to the result vector. We stop when all
         * posibilities are found
         */
        public synchronized int scan(final byte[] dataArg, final int offset, final Collection<int[]> result) {
            this.data = dataArg;
            return scan(offset, result);
        }

        public synchronized int scan(final int curPosArg, final Collection<int[]> result) {
            if (!(curPosArg < this.data.length)) {
                return curPosArg;
            }
            State state = this.root;
            int length = 0;
            int curPos = curPosArg;
            while ((state != null) && (curPos >= 0)) {
                if (curPos > this.data.length || curPos < 0) {
                    return curPos;
                }
                int ch = this.data[curPos];
                if (ch < 0) {
                    ch += Byte.MAX_VALUE - Byte.MIN_VALUE;
                }
                state = state.nextStates[ch];
                length++;
                if (state != null && state.matches != null) {
                    for (int i = 0; i < state.matches.length; i++) {
                        final int id = state.matches[i];
                        final int[] tmp = new int[3];
                        tmp[0] = curPos;
                        tmp[1] = id;
                        tmp[2] = length;
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
            public int gotHereBy;

            // A list of keyword ids that are matched at this state.
            public int[] matches = null;

            // constructor
            public State(final int gotHereBy) {
                this.gotHereBy = gotHereBy;
            }

            public void learn(final byte[] word, final int id) throws Exception {
                learn(word, word.length - 1, id);
            }

            /**
             * Walk throught he keyword backwards. Adding states to the root (or current state) when they don't exists. At the end,
             * record the keyowrd id in the ending state.
             *
             * Warning this is recursive, but thats OK for small keywords.
             */
            public void learn(final byte[] word, final int wordLoc, final int id) throws Exception {
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
                    if (this.matches == null) {
                        this.matches = new int[0];
                    }
                    final int[] newMatches = new int[this.matches.length + 1];
                    System.arraycopy(this.matches, 0, newMatches, 0, this.matches.length);
                    this.matches = newMatches;
                    this.matches[this.matches.length - 1] = id;
                } else {
                    // Get the next character in the word
                    int nextChar = word[wordLoc];
                    if (nextChar < 0) {
                        nextChar += Byte.MAX_VALUE - Byte.MIN_VALUE;
                    }

                    // See if the state already exists
                    State nextState = this.nextStates[nextChar];
                    if (nextState == null) {
                        // Make a new state because it isn't there yet.
                        nextState = this.nextStates[nextChar] = new State(nextChar);
                    }
                    // Learn the rest of the keyword in the new state.
                    nextState.learn(word, wordLoc - 1, id);
                }
            }

            public void print(final PrintStream out) {
                print(out, "root:");
            }

            // Make a pretty picture.
            public void print(final PrintStream out, final String prefix) {
                if ((this.gotHereBy < ' ') || (this.gotHereBy > '~')) {
                    out.println(prefix + "-> " + "(byte)" + this.gotHereBy);
                } else {
                    out.println(prefix + "-> " + (char) this.gotHereBy);
                }
                if (this.matches != null) {
                    out.print(prefix + "ids [");
                    for (int i = 0; i < this.matches.length; i++) {
                        out.print(" " + this.matches[i]);
                    }
                    out.println(" ]");
                }
                for (int i = 0; i < this.nextStates.length; i++) {
                    if (this.nextStates[i] != null) {
                        this.nextStates[i].print(out, prefix + "  ");
                    }
                }
            }
        }
    }
}
