package eu.amadeos.apps.fontviewer.domain;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FittingSampleTest {
    static Font caladea() throws Exception {
        try (InputStream in = FittingSampleTest.class.getResourceAsStream("/fonts/Caladea-Regular.ttf")) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }

    @Test
    void keepsATextTheFontCanShow() throws Exception {
        Font font = caladea();
        String sentence = "The quick brown fox jumps over the lazy dog.";
        assertTrue(FittingSample.fits(font, sentence));
        assertEquals(sentence, FittingSample.forFont(font, sentence));
        assertTrue(FittingSample.fits(font, "Fox ☃"), "one missing sign of three is not enough to switch");
        assertNull(FittingSample.forFont(null, null));
    }

    @Test
    void showsTheFontsOwnCharactersInsteadOfBoxes() throws Exception {
        Font font = caladea();
        // Caladea has no Devanagari: as for a Noto script font with a Latin sample, nothing would show.
        String devanagari = "नमस्ते दुनिया";
        assertFalse(FittingSample.fits(font, devanagari));
        String shown = FittingSample.forFont(font, devanagari);
        assertNotEquals(devanagari, shown);
        assertFalse(shown.isBlank());
        assertTrue(shown.codePointCount(0, shown.length()) <= FittingSample.LENGTH + FittingSample.LENGTH / FittingSample.GROUP);
        assertTrue(shown.codePoints().filter(c -> c != ' ').allMatch(font::canDisplay), shown);
        assertTrue(shown.codePoints().filter(c -> c != ' ')
                .noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN), shown);
        assertEquals(shown, FittingSample.ownCharacters(font), "looked up once and kept");
    }
}
