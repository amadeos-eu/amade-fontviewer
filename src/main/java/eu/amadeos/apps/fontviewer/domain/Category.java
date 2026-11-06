package eu.amadeos.apps.fontviewer.domain;

import java.util.Locale;

/** The kind of a font family, for the filter of the list and the line under the family's name. */
public enum Category {
    SERIF, SANS_SERIF, MONOSPACE, HANDWRITING, DISPLAY, SYMBOLS;

    /** The key of the name in the messages, {@code category.sans-serif}. */
    public String textKey() {
        return "category." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Works the kind out from the PANOSE classification in the font's OS/2 table, with the family name and
     * the fixed width as hints for fonts that leave PANOSE empty, as many do.
     *
     * @param panoseFamily  bFamilyType: 2 Latin text, 3 hand written, 4 decorative, 5 symbol; 0 or 1 = no data
     * @param panoseSerif   bSerifStyle of a Latin text font: 11 to 13 are the sans serif styles
     * @param monospace     fontconfig says every glyph has the same width
     */
    public static Category of(String family, int panoseFamily, int panoseSerif, boolean monospace) {
        return of(family, panoseFamily, panoseSerif, monospace, () -> false);
    }

    /**
     * As above; when neither the name nor the PANOSE data tell serif from sans serif (many free fonts leave
     * PANOSE empty), {@code serifShape} decides by looking at the letters.
     */
    public static Category of(String family, int panoseFamily, int panoseSerif, boolean monospace,
                              java.util.function.BooleanSupplier serifShape) {
        String name = family.toLowerCase(Locale.ROOT);
        if (monospace || name.contains("mono") || name.contains("courier") || name.contains("code")) {
            return MONOSPACE;
        }
        if (name.contains("emoji") || name.contains("symbol") || name.contains("dingbat")
                || name.contains("icons") || panoseFamily == 5) {
            return SYMBOLS;
        }
        if (panoseFamily == 3 || name.contains("script") || name.contains("hand")) return HANDWRITING;
        if (panoseFamily == 4) return DISPLAY;
        if (name.contains("sans") || name.contains("gothic")) return SANS_SERIF;
        if (name.contains("serif") || name.contains("roman") || name.contains("mincho")) return SERIF;
        if (panoseFamily == 2 && panoseSerif >= 2 && panoseSerif <= 10) return SERIF;
        if (panoseFamily == 2 && panoseSerif >= 11 && panoseSerif <= 13) return SANS_SERIF;
        return serifShape.getAsBoolean() ? SERIF : SANS_SERIF;
    }
}
