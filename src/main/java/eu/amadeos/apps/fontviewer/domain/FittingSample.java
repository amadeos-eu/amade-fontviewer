package eu.amadeos.apps.fontviewer.domain;

import java.awt.Font;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;

/**
 * A sample text the font can actually show. Many fonts have no Latin letters at all (Noto Sans Arabic,
 * Noto Sans Devanagari, the cuneiform and hieroglyph fonts, Dingbats and other symbol fonts); with a Latin
 * sample they would show nothing but boxes. For them the preview shows the font's own characters instead:
 * the letters of the script the font is made for, or its symbols when it has no script.
 */
public final class FittingSample {
    /** At most this many characters in a sample made from the font. */
    static final int LENGTH = 48;
    /** Characters per group; the groups are separated by spaces so that long samples can wrap. */
    static final int GROUP = 8;
    /** The highest character looked at: the basic and the supplementary multilingual planes. */
    private static final int LAST = 0x1FFFF;

    /** The own characters of the fonts asked so far (looking them up walks through 130,000 characters). */
    private static final Map<Font, String> OWN = Collections.synchronizedMap(new WeakHashMap<>());

    private FittingSample() {
    }

    /** True when the font shows at least half of the visible characters of the text. */
    public static boolean fits(Font font, String text) {
        int[] visible = text.codePoints().filter(c -> !Character.isWhitespace(c)).toArray();
        if (visible.length == 0) return true;
        long shown = Arrays.stream(visible).filter(font::canDisplay).count();
        return shown * 2 >= visible.length;
    }

    /** The text itself when the font can show it, otherwise a sample made of the font's own characters. */
    public static String forFont(Font font, String text) {
        if (font == null || text == null || fits(font, text)) return text;
        String own = ownCharacters(font);
        return own.isEmpty() ? text : own;
    }

    /**
     * The letters of the script the font has most letters of (Latin and the shared characters left aside),
     * or, for symbol fonts, its symbols; empty when the font shows nothing at all.
     */
    public static String ownCharacters(Font font) {
        return OWN.computeIfAbsent(font, FittingSample::lookUp);
    }

    private static String lookUp(Font font) {
        Map<Character.UnicodeScript, Integer> letters = new EnumMap<>(Character.UnicodeScript.class);
        for (int c = 0x20; c <= LAST; c++) {
            if (!Character.isLetter(c) || !font.canDisplay(c)) continue;
            Character.UnicodeScript script = Character.UnicodeScript.of(c);
            if (script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED
                    || script == Character.UnicodeScript.LATIN) continue;
            letters.merge(script, 1, Integer::sum);
        }
        Character.UnicodeScript best = letters.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        if (best != null) {
            return take(font, c -> Character.isLetter(c) && Character.UnicodeScript.of(c) == best);
        }
        // No script of its own: symbols, dingbats, music, numbers ... Beyond ASCII first, as symbol fonts
        // often carry the digits and # * of the keyboard too.
        IntPredicate symbol = c -> {
            int type = Character.getType(c);
            return type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL
                    || type == Character.CURRENCY_SYMBOL || type == Character.MODIFIER_SYMBOL
                    || type == Character.OTHER_NUMBER || type == Character.DECIMAL_DIGIT_NUMBER
                    || type == Character.OTHER_PUNCTUATION;
        };
        String beyondAscii = take(font, c -> c > 0x7F && symbol.test(c));
        return beyondAscii.isEmpty() ? take(font, symbol) : beyondAscii;
    }

    private static String take(Font font, IntPredicate wanted) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int c = 0x21; c <= LAST && count < LENGTH; c++) {
            if (!wanted.test(c) || !font.canDisplay(c)) continue;
            if (count > 0 && count % GROUP == 0) sb.append(' ');
            sb.appendCodePoint(c);
            count++;
        }
        return sb.toString();
    }
}
