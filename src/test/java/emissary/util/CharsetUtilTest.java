package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharsetUtilTest extends UnitTest {
    // Strings from wikipedia, 2017-07-18
    // If this test fails, (why else would you be looking here?)
    // one thing to look at is to ensure "javac -encoding=utf8"
    // is being turned on so that these strings make it into the
    // .class file as utf8.
    public static final String[] S = new String[] {"L'ordinateur à 100$ bientôt sur le marché ?",
            "Um dicionário é uma compilação de palavras ou dos termos próprios",
            "Un dictionnaire est un ouvrage de référence contenant l’ensemble des mots d’une langue",
            "Sözlük bir dilin veya dillerin kelime haznesini", "القاموس هو أداة لجمع كلمات لغة ما وتعريفها وشرحها",
            "Слова́рь это книга, информация в которой упорядочена c помощью разбивки на небольшие статьи, отсортированные по названию или тематике",
            "稱辭典，是為詞語提供音韻、釋義、例句用法等等的工具書。", "Từ điển là danh sách các từ, ngữ được sắp xếp thành các từ vị chuẩn",
            "Un dicționar sau lexicon este o lucrare lexicografică care cuprinde o parte semnificativă dintre cuvintele unei limbi",
            "د مڼې، پیاز او الو خوند یو ډول دی، مګر د مختلف ډوله وږم لرلو سره د دوي خوند مختلف ښکاري"};

    @Test
    void testUTF8() {
        assertTrue(CharsetUtil.isUtf8("This is a test."), "Ascii is utf8");
        assertTrue(CharsetUtil.isUtf8("!@#$%^&*(F)=+-_[]{}\\|'\";:,.></?`~"), "Punctuation is utf8");
        assertTrue(CharsetUtil.isUtf8("0123456789 9876543210"), "Numbers are utf8");

        assertTrue(CharsetUtil.isUtf8("This is a bytes array test. 123 #$%".getBytes()), "Ascii bytes are utf8");

        for (int i = 0; i < S.length; i++) {
            assertTrue(CharsetUtil.isUtf8(S[i]), "Foreign strings from java, entry " + i + " of " + S.length + " == " + S[i] + "/" + S[i].length());
            assertTrue(CharsetUtil.isUtf8(S[i].getBytes()),
                    "Foreign bytes from java, entry " + i + " of " + S.length + " == " + S[i] + "/" + S[i].getBytes().length);

        }
    }

    @Test
    void testMultibyte() {
        for (int i = 0; i < S.length; i++) {
            assertTrue(CharsetUtil.hasMultibyte(S[i]),
                    "Foreign strings have multibyte, entry " + i + " of " + S.length + " == " + S[i] + "/" + S[i].length());
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

}
