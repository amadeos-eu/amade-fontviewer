package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.Outlines;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Details and characters of a font file: from its tables when it is TrueType or OpenType, from Java's font
 * otherwise (Type 1). Results are kept per file and face.
 */
public class FontInspector {
    /** What the inspector found: the details and the characters. */
    public record Inspection(FontDetails details, Coverage coverage) {
    }

    private final FontLoader loader;
    private final Map<String, Inspection> cache = new ConcurrentHashMap<>();

    public FontInspector(FontLoader loader) {
        this.loader = loader;
    }

    public Inspection inspect(Path file, int index) {
        return cache.computeIfAbsent(file.toAbsolutePath() + "#" + index, k -> read(file, index));
    }

    public void forget(Path file) {
        String prefix = file.toAbsolutePath() + "#";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** "Bold Italic" from Java's names ("Charter Bold Italic" in family "Charter"). */
    static String styleOf(Font f) {
        String name = f.getFontName();
        String family = f.getFamily();
        String rest = name.startsWith(family) ? name.substring(family.length()).replaceAll("[-._]", " ").strip() : "";
        return rest.isEmpty() || rest.equalsIgnoreCase("plain") ? "Regular" : rest;
    }

    private Inspection read(Path file, int index) {
        try {
            FontTables tables = FontTables.read(file, index);
            return new Inspection(tables.details(), tables.coverage());
        } catch (IOException e) {
            Optional<Font> font = loader.font(file, index);
            if (font.isEmpty()) return new Inspection(FontDetails.NONE, Coverage.EMPTY);
            Font f = font.get();
            int[] chars = IntStream.rangeClosed(0x20, 0xFFFF).filter(f::canDisplay).toArray();
            boolean type1 = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).matches(".*\\.pf[ab]");
            FontDetails d = new FontDetails(f.getFontName(), "", "", "", "", "", "", "", "", f.getNumGlyphs(),
                    type1 ? Outlines.TYPE1 : Outlines.UNKNOWN, false, false, 0, 0, f.getFamily(), styleOf(f));
            return new Inspection(d, Coverage.of(chars));
        }
    }
}
