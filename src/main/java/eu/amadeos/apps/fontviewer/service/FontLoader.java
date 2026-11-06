package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFiles;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes Java fonts straight from the font files, so that the preview shows exactly the file, also one that is
 * not installed or was installed after this program started (Java reads the system's font list only once).
 * Fonts are kept, at size 1; callers derive the size they need.
 */
public class FontLoader {
    private final Map<String, Optional<Font>> cache = new ConcurrentHashMap<>();

    /** The font of this face, or empty when Java cannot read it (bitmap fonts, broken files). */
    public Optional<Font> font(FontFace face) {
        if (!face.drawable()) return Optional.empty();
        return font(face.file(), face.index());
    }

    public Optional<Font> font(Path file, int index) {
        return cache.computeIfAbsent(file.toAbsolutePath() + "#" + index, key -> load(file, index));
    }

    /** The font if it was loaded before; never reads a file (for list cells, which must paint fast). */
    public Optional<Font> cached(FontFace face) {
        Optional<Font> font = cache.get(face.file().toAbsolutePath() + "#" + face.index());
        return font == null ? Optional.empty() : font;
    }

    /** Forgets a file, after it was removed or replaced. */
    public void forget(Path file) {
        String prefix = file.toAbsolutePath() + "#";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static Optional<Font> load(Path file, int index) {
        try {
            String ext = FontFiles.extension(file).orElse("");
            if (ext.equals("pfb") || ext.equals("pfa")) {
                return Optional.of(Font.createFont(Font.TYPE1_FONT, file.toFile()));
            }
            Font[] fonts = Font.createFonts(file.toFile());
            if (index < 0 || index >= fonts.length) return Optional.empty();
            return Optional.of(fonts[index]);
        } catch (IOException | FontFormatException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
