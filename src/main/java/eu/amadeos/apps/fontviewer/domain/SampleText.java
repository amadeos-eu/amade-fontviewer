package eu.amadeos.apps.fontviewer.domain;

import java.util.Locale;

/** The texts the preview can show; {@link #OWN} is whatever the user typed. */
public enum SampleText {
    SENTENCE, ALPHABET, NUMBERS, PARAGRAPH, OWN;

    public static final String ALPHABET_TEXT = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz ÄÖÜ äöüß";
    public static final String NUMBERS_TEXT = "0123456789 +-×÷=% €$£¥ (1/2) [§&@#] .,:;!?\"'«»";

    public String textKey() {
        return "sample." + name().toLowerCase(Locale.ROOT);
    }
}
