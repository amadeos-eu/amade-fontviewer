package eu.amadeos.apps.fontviewer.domain;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * One style of a font family as fontconfig reports it: a file (and the face in it, for collections),
 * the family and style names, weight and slant on fontconfig's scales.
 *
 * @param weight    fontconfig weight: 40 light, 80 regular, 100 medium, 180 semi bold, 200 bold, 210 black
 * @param slant     0 upright, 100 italic, 110 oblique
 * @param languages the fontconfig language codes the font covers ({@code de}, {@code pl}, {@code zh-cn} ...)
 */
public record FontFace(Path file, int index, String family, String style, int weight, int slant,
                       boolean monospace, String format, Set<String> languages, FontScope scope) {
    public static final int REGULAR = 80;

    public FontFace {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(family, "family");
        style = style == null || style.isBlank() ? "Regular" : style;
        format = format == null ? "" : format;
        languages = languages == null ? Set.of() : Set.copyOf(languages);
        Objects.requireNonNull(scope, "scope");
    }

    public boolean italic() {
        return slant > 0;
    }

    /** "Inter Bold", or just the family for its regular style. */
    public String fullName() {
        return style.equalsIgnoreCase("Regular") ? family : family + " " + style;
    }

    /** How far this face is from the plain upright regular style; the family shows the nearest first. */
    int distanceFromRegular() {
        return Math.abs(weight - REGULAR) + (italic() ? 1000 : 0);
    }

    /** Faces that fontconfig can show but Java cannot draw (bitmap fonts in PCF or BDF). */
    public boolean drawable() {
        String f = format.toLowerCase(java.util.Locale.ROOT);
        return !(f.equals("pcf") || f.equals("bdf") || f.equals("pfr"));
    }
}
