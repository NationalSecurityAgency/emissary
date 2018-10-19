package emissary.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class CharsetUtilTest extends UnitTest {
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
    public void testUTF8() {
        assertTrue("Ascii is utf8", CharsetUtil.isUTF8("This is a test."));
        assertTrue("Punctuation is utf8", CharsetUtil.isUTF8("!@#$%^&*(F)=+-_[]{}\\|'\";:,.></?`~"));
        assertTrue("Numbers are utf8", CharsetUtil.isUTF8("0123456789 9876543210"));

        assertTrue("Ascii bytes are utf8", CharsetUtil.isUTF8("This is a bytes array test. 123 #$%".getBytes()));

        for (int i = 0; i < S.length; i++) {
            assertTrue("Foreign strings from java, entry " + i + " of " + S.length + " == " + S[i] + "/" + S[i].length(), CharsetUtil.isUTF8(S[i]));
            assertTrue("Foreign bytes from java, entry " + i + " of " + S.length + " == " + S[i] + "/" + S[i].getBytes().length,
                    CharsetUtil.isUTF8(S[i].getBytes()));

        }
    }

    @Test
    public void testMultibyte() {
        for (int i = 0; i < S.length; i++) {
            assertTrue("Foreign strings have multibyte, entry " + i + " of " + S.length + " == " + S[i] + "/" + S[i].length(),
                    CharsetUtil.hasMultibyte(S[i]));
        }

        assertFalse("Null does not have multibyte", CharsetUtil.hasMultibyte(null));

        assertFalse("Empty does not have multibyte", CharsetUtil.hasMultibyte(""));

        assertFalse("Ascii does not have multibyte", CharsetUtil.hasMultibyte("1234 abcde !@#$%"));
    }

    @Test
    public void testNotUTF8() {
        byte[] b = new byte[] {(byte) 192, (byte) 192, (byte) 224, (byte) 224, (byte) 192, (byte) 0, (byte) 192, (byte) 224};
        assertFalse("Bad utf-8 stream is not utf-8", CharsetUtil.isUTF8(b));
    }

    @Test
    public void testIsAscii() {
        assertTrue("Ascii is easy", CharsetUtil.isAscii("abcdefg 1234567"));
        assertFalse("This is not ascii", CharsetUtil.isAscii("Шарифа"));
    }

}
