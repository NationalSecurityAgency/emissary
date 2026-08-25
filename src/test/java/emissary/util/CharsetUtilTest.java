package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharsetUtilTest extends UnitTest {
    // Strings from wikipedia, 2017-07-18
    // If this test fails, (why else would you be looking here?)
    // one thing to look at is to ensure "javac -encoding=utf8"
    // is being turned on so that these strings make it into the
    // .class file as utf8.
    public static final List<String> S = List.of("L'ordinateur à 100$ bientôt sur le marché ?",
            "Um dicionário é uma compilação de palavras ou dos termos próprios",
            "Un dictionnaire est un ouvrage de référence contenant l’ensemble des mots d’une langue",
            "Sözlük bir dilin veya dillerin kelime haznesini", "القاموس هو أداة لجمع كلمات لغة ما وتعريفها وشرحها",
            "Слова́рь это книга, информация в которой упорядочена c помощью разбивки на небольшие статьи, отсортированные по названию или тематике",
            "稱辭典，是為詞語提供音韻、釋義、例句用法等等的工具書。", "Từ điển là danh sách các từ, ngữ được sắp xếp thành các từ vị chuẩn",
            "Un dicționar sau lexicon este o lucrare lexicografică care cuprinde o parte semnificativă dintre cuvintele unei limbi",
            "د مڼې، پیاز او الو خوند یو ډول دی، مګر د مختلف ډوله وږم لرلو سره د دوي خوند مختلف ښکاري");

    @Test
    void testUTF8() {
        assertTrue(CharsetUtil.isUtf8("This is a test."), "Ascii is utf8");
        assertTrue(CharsetUtil.isUtf8("!@#$%^&*(F)=+-_[]{}\\|'\";:,.></?`~"), "Punctuation is utf8");
        assertTrue(CharsetUtil.isUtf8("0123456789 9876543210"), "Numbers are utf8");

        assertTrue(CharsetUtil.isUtf8("This is a bytes array test. 123 #$%".getBytes(UTF_8)), "Ascii bytes are utf8");

        for (int i = 0; i < S.size(); i++) {
            assertTrue(CharsetUtil.isUtf8(S.get(i)),
                    "Foreign strings from java, entry " + i + " of " + S.size() + " == " + S.get(i) + "/" + S.get(i).length());
            assertTrue(CharsetUtil.isUtf8(S.get(i).getBytes(UTF_8)),
                    "Foreign bytes from java, entry " + i + " of " + S.size() + " == " + S.get(i) + "/" + S.get(i).getBytes(UTF_8).length);
        }
    }

    @Test
    void testMultibyte() {
        for (int i = 0; i < S.size(); i++) {
            assertTrue(CharsetUtil.hasMultibyte(S.get(i)),
                    "Foreign strings have multibyte, entry " + i + " of " + S.size() + " == " + S.get(i) + "/" + S.get(i).length());
        }

        assertFalse(CharsetUtil.hasMultibyte(null), "Null does not have multibyte");

        assertFalse(CharsetUtil.hasMultibyte(""), "Empty does not have multibyte");

        assertFalse(CharsetUtil.hasMultibyte("1234 abcde !@#$%"), "Ascii does not have multibyte");
    }

    @Test
    void testNotUTF8() {
        byte[] b = new byte[] {(byte) 192, (byte) 192, (byte) 224, (byte) 224, (byte) 192, (byte) 0, (byte) 192, (byte) 224};
        assertFalse(CharsetUtil.isUtf8(b), "Bad utf-8 stream is not utf-8");
    }

    @Test
    void testIsAscii() {
        assertTrue(CharsetUtil.isAscii("abcdefg 1234567"), "Ascii is easy");
        assertFalse(CharsetUtil.isAscii("Шарифа"), "This is not ascii");
    }

    @Test
    void byteToCharArrayIsUtf8() {
        final String s = "This is a test. 123 #$%";
        assertArrayEquals(s.toCharArray(), CharsetUtil.byteToCharArray(s.getBytes(UTF_8)), "ascii bytes decode");
    }

    @Test
    void byteToCharArrayDecodesMultibyteUtf8() {
        final String s = S.get(0);
        assertArrayEquals(s.toCharArray(), CharsetUtil.byteToCharArray(s.getBytes(UTF_8)), "utf8 bytes decode");
    }

    @Test
    void jGetUtfCharArrayNullCharsetUsesLatin1Fallback() {
        final byte[] bytes = new byte[] {0, 1, 0x7f, (byte) 0x80, (byte) 0xff};
        final char[] expected = new char[] {0, 1, 0x7f, (char) 0x80, (char) 0xff};
        assertArrayEquals(expected, CharsetUtil.jGetUtfCharArray(bytes, null, 0, -1), "latin-1 for null charset");
    }

    @Test
    void getUtfCharArrayWithCharset() {
        final String s = S.get(1);
        assertArrayEquals(s.toCharArray(), CharsetUtil.getUtfCharArray(s.getBytes(UTF_8), "UTF-8", 0, -1), "known charset decode");
    }
}
