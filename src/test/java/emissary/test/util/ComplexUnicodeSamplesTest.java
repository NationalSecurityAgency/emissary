package emissary.test.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ComplexUnicodeSamplesTest {

    /**
     * Interesting observations about face palm dude emoji.
     * <p>
     * We’ve seen four different lengths so far:
     * 
     * <ul>
     * <li>Number of UTF-8 code units (17 in this case)</li>
     * <li>Number of UTF-16 code units (7 in this case)</li>
     * <li>Number of UTF-32 code units or Unicode scalar values (5 in this case)</li>
     * <li>Number of extended grapheme clusters (1 in this case)</li>
     * </ul>
     * Given a valid Unicode string and a version of Unicode, all of the above are well-defined and it holds that each item
     * higher on the list is greater or equal than the items lower on the list.
     * <p>
     * One of these is not like the others, though: The first three numbers have an unchanging definition for any valid
     * Unicode string whether it contains currently assigned scalar values or whether it is from the future and contains
     * unassigned scalar values as far as software written today is aware. Also, computing the first three lengths does not
     * involve lookups from the Unicode database. However, the last item depends on the Unicode version and involves lookups
     * from the Unicode database. If a string contains scalar values that are unassigned as far as the copy of the Unicode
     * database that the program is using is aware, the program will potentially overcount extended grapheme clusters in the
     * string compared to a program whose copy of the Unicode database is newer and has assignments for those scalar values
     * (and some of those assignments turn out to be combining characters).
     */
    @Test
    void demonstrateMetadataAboutFacePalmDude() {

        String facepalm = ComplexUnicodeSamples.getFacePalmingMaleControlSkintone();

        assertEquals(5, facepalm.codePointCount(0, facepalm.length()));

        // ICU4J gets it right
        assertEquals(1, ComplexUnicodeSamples.countGraphemesUsingIcu4J(facepalm));

        // Java earlier than Java 20 gets it wrong, this test can be updated as we move versions of java
        // but it's important to know/highlight the difference.
        assertEquals(4, ComplexUnicodeSamples.countGraphemesUsingJavaBuiltInBreakIterator(facepalm));

        // SCALAR 1 is 4 UTF8 bytes
        // SCALAR 2 is 4 UTF8 bytes
        // SCALAR 3 is 3 UTF8 bytes
        // SCALAR 4 is 3 UTF8 bytes
        // SCALAR 5 is 3 UTF8 bytes
        // TOTAL : 17 UTF8 bytes
        assertEquals(17, facepalm.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(facepalm, new String(facepalm.getBytes(StandardCharsets.UTF_8)));

        // SCALAR 1 is 4 UTF16 bytes
        // SCALAR 2 is 4 UTF16 bytes
        // SCALAR 3 is 2 UTF16 bytes
        // SCALAR 4 is 2 UTF16 bytes
        // SCALAR 5 is 2 UTF16 bytes
        // TOTAL : 14 UTF16 bytes if no BOM is needed
        // Java typically defaults to UTF-16BE
        assertEquals(14, facepalm.getBytes(StandardCharsets.UTF_16BE).length);
        assertEquals(facepalm, new String(facepalm.getBytes(StandardCharsets.UTF_16BE), StandardCharsets.UTF_16BE));
        assertEquals(14, facepalm.getBytes(StandardCharsets.UTF_16LE).length);
        assertEquals(facepalm, new String(facepalm.getBytes(StandardCharsets.UTF_16LE), StandardCharsets.UTF_16LE));

        // When the endianness isn't specified, 2 bytes are used for the byte order marker
        // The BOM is a special character (U+FEFF) used to indicate the endianness (byte order)
        // of a UTF-16 encoded file or stream. In UTF-16, the BOM can be either:
        // FE FF (Big Endian)
        // FF FE (Little Endian)
        assertEquals(16, facepalm.getBytes(StandardCharsets.UTF_16).length);
        assertEquals(facepalm, new String(facepalm.getBytes(StandardCharsets.UTF_16), StandardCharsets.UTF_16));

        // 5 UTF-32 characters at 4 bytes per character
        assertEquals(20, facepalm.getBytes(Charset.forName("UTF-32")).length);
        assertEquals(facepalm, new String(facepalm.getBytes(Charset.forName("UTF-32")), Charset.forName("UTF-32")));

        // single byte encoding is not going to produce what you want
        assertEquals(5, facepalm.getBytes(StandardCharsets.ISO_8859_1).length);
        assertNotEquals(facepalm, new String(facepalm.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1));


    }

    @Test
    void demonstrateMetadataAboutFlagOfItaly() {
        String flagOfItaly = ComplexUnicodeSamples.getFlagOfItaly();

        // ICU4J gets it right
        assertEquals(1, ComplexUnicodeSamples.countGraphemesUsingIcu4J(flagOfItaly));

        // Java gets it wrong
        assertEquals(2, ComplexUnicodeSamples.countGraphemesUsingJavaBuiltInBreakIterator(flagOfItaly));

        // 4 UTF-8 characters at 2 bytes per character
        assertEquals(8, flagOfItaly.getBytes(StandardCharsets.UTF_8).length);

        // TODO: explain
        assertEquals(10, flagOfItaly.getBytes(StandardCharsets.UTF_16).length);
        assertEquals(8, flagOfItaly.getBytes(StandardCharsets.UTF_16BE).length);
        assertEquals(8, flagOfItaly.getBytes(StandardCharsets.UTF_16LE).length);

        // 2 UTF-32 characters at 4 bytes per character
        assertEquals(8, flagOfItaly.getBytes(Charset.forName("UTF-32")).length);

        assertEquals(2, flagOfItaly.getBytes(StandardCharsets.ISO_8859_1).length);

        assertEquals(2, flagOfItaly.codePointCount(0, flagOfItaly.length()));
    }


}
