package eu.amadeos.apps.fontviewer.service;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tells serif from sans serif fonts by the shape of the letters I and l, for fonts whose files do not say it: in a
 * sans serif font they are plain bars, exactly as wide at the foot as in the middle; in a serif font the foot
 * is clearly wider.
 */
public final class SerifShape {
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    /** A serif I is at least this much wider at its foot than in the middle. */
    static final double SERIF_RATIO = 1.3;

    private SerifShape() {
    }

    /** False when the file cannot be read or lacks the letters. */
    public static boolean hasSerifs(Path file, int index) {
        if (!Files.isRegularFile(file)) return false;
        try {
            Font[] fonts = Font.createFonts(file.toFile());
            return index < fonts.length && hasSerifs(fonts[index]);
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    public static boolean hasSerifs(Font font) {
        return standsOnFoot(font, "I") && standsOnFoot(font, "l");
    }

    private static boolean standsOnFoot(Font font, String letter) {
        if (!font.canDisplay(letter.charAt(0))) return false;
        Shape outline = font.deriveFont(100f).createGlyphVector(CONTEXT, letter).getOutline();
        Rectangle2D box = outline.getBounds2D();
        if (box.getHeight() <= 0 || box.getWidth() <= 0) return false;
        double stem = widthAt(outline, box, 0.5);
        double foot = widthAt(outline, box, 0.03);
        return stem > 0 && foot / stem >= SERIF_RATIO;
    }

    /** The width of the letter in a thin strip at a height (0 = bottom, 1 = top). */
    private static double widthAt(Shape outline, Rectangle2D box, double height) {
        double y = box.getMaxY() - box.getHeight() * height;
        Area strip = new Area(new Rectangle2D.Double(box.getMinX() - 1, y - 0.5, box.getWidth() + 2, 1));
        strip.intersect(new Area(outline));
        return strip.isEmpty() ? 0 : strip.getBounds2D().getWidth();
    }
}
