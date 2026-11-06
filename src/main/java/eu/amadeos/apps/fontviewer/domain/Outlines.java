package eu.amadeos.apps.fontviewer.domain;

/** How the glyphs of a font file are drawn. */
public enum Outlines {
    /** {@code glyf} table: TrueType quadratic outlines. */
    TRUETYPE,
    /** {@code CFF } or {@code CFF2} table: PostScript cubic outlines in an OpenType file. */
    POSTSCRIPT,
    /** An old PostScript Type 1 file ({@code .pfb}, {@code .pfa}). */
    TYPE1,
    /** Anything else (bitmap fonts, unreadable files). */
    UNKNOWN;

    public String textKey() {
        return "outlines." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
