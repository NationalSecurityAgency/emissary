package emissary.util;

import java.util.Arrays;

/**
 * A set of named counters for keeping counts on various classes of characters encountered
 */
public class CharacterCounterSet extends CounterSet {
    private static final long serialVersionUID = -7111758159975960091L;
    public static final String[] CHARACTER_TYPE_KEYS = {"CHARACTER_LETTER", "CHARACTER_DIGIT", "CHARACTER_WHITESPACE", "CHARACTER_ISO_CONTROL",
            "CHARACTER_PUNCTUATION", "CHARACTER_OTHER"};

    /**
     * Create a set of character counters
     */
    public CharacterCounterSet() {
        loadCharacterKeys();
    }

    /**
     * Create a set of character counters
     * 
     * @param initialCapacity the hash map initial capacity
     */
    public CharacterCounterSet(int initialCapacity) {
        super(initialCapacity);
        loadCharacterKeys();
    }

    /**
     * Create a set of character counters
     * 
     * @param initialCapacity the hash map initial capacity
     * @param loadFactor the hash map load factor
     */
    public CharacterCounterSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        loadCharacterKeys();
    }

    /**
     * Load in our special character class keys
     */
    protected void loadCharacterKeys() {
        addKeys(Arrays.asList(CHARACTER_TYPE_KEYS));
    }


    /**
     * Easy access to letters
     */
    public int getLetterCount() {
        return get("CHARACTER_LETTER");
    }

    /**
     * Easy access to digits
     */
    public int getDigitCount() {
        return get("CHARACTER_DIGIT");
    }

    /**
     * Easy access to whitespace
     */
    public int getWhitespaceCount() {
        return get("CHARACTER_WHITESPACE");
    }

    /**
     * Easy access to control
     */
    public int getISOControlCount() {
        return get("CHARACTER_ISO_CONTROL");
    }

    /**
     * Easy access to punctuation
     */
    public int getPunctuationCount() {
        return get("CHARACTER_PUNCTUATION");
    }

    /**
     * Easy access to other
     */
    public int getOtherCount() {
        return get("CHARACTER_OTHER");
    }


    /**
     * Count the characters in s by class. This works by codepoint and handles codepoints beyond the BMP
     * 
     * @param s the string to perform the count on
     */
    public void count(String s) {
        int cpc = s.codePointCount(0, s.length());
        for (int i = 0; i < cpc; i++) {
            int offset = s.offsetByCodePoints(0, i);
            int cp = s.codePointAt(offset);
            int[] cpa = {cp};
            String scp = new String(cpa, 0, 1);
            if (Character.isLetter(cp))
                increment("CHARACTER_LETTER");
            else if (Character.isDigit(cp))
                increment("CHARACTER_DIGIT");
            else if (Character.isSpaceChar(cp))
                increment("CHARACTER_WHITESPACE");
            else if (Character.isISOControl(cp))
                increment("CHARACTER_ISO_CONTROL");
            else if (scp.matches("[\\p{Punct}\\p{InGeneralPunctuation}\\u00a1-\\u00bf\\uff01-\\uff0f\\uff1a-\\uff20\\uff38-\\uff40\\uff5b-\\uff60]"))
                increment("CHARACTER_PUNCTUATION");
            else
                increment("CHARACTER_OTHER");
        }
    }
}
