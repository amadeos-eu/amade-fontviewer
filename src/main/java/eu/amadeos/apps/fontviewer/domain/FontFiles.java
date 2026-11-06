package eu.amadeos.apps.fontviewer.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Which files are fonts this program can open and install. */
public final class FontFiles {
    /** The endings the file chooser, the drop target and the installer accept. */
    public static final List<String> EXTENSIONS = List.of("ttf", "otf", "ttc", "otc", "pfb", "pfa");
    /** The MIME types the program opens (see the desktop file). */
    public static final List<String> MIME_TYPES = List.of("font/ttf", "font/otf", "font/collection",
            "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype",
            "application/x-font-type1");

    private FontFiles() {
    }

    public static boolean hasFontExtension(Path file) {
        return extension(file).map(EXTENSIONS::contains).orElse(false);
    }

    public static Optional<String> extension(Path file) {
        Path name = file.getFileName();
        if (name == null) return Optional.empty();
        String s = name.toString();
        int dot = s.lastIndexOf('.');
        return dot <= 0 ? Optional.empty() : Optional.of(s.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * True when the file starts like a font: TrueType ({@code 00 01 00 00} or {@code true}), OpenType with
     * PostScript outlines ({@code OTTO}), a collection ({@code ttcf}) or a Type 1 font ({@code 80 01} for
     * .pfb, {@code %!PS-AdobeFont} or {@code %!FontType1} for .pfa). The installer only takes such files.
     */
    public static boolean looksLikeFont(byte[] head) {
        if (head.length < 4) return false;
        int b0 = head[0] & 0xFF, b1 = head[1] & 0xFF, b2 = head[2] & 0xFF, b3 = head[3] & 0xFF;
        if (b0 == 0 && b1 == 1 && b2 == 0 && b3 == 0) return true;
        String tag = new String(head, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (tag.equals("OTTO") || tag.equals("true") || tag.equals("ttcf") || tag.equals("typ1")) return true;
        if (b0 == 0x80 && b1 == 0x01) return true;
        String start = new String(head, 0, Math.min(head.length, 20), java.nio.charset.StandardCharsets.ISO_8859_1);
        return start.startsWith("%!PS-AdobeFont") || start.startsWith("%!FontType1");
    }

    public static boolean looksLikeFont(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return looksLikeFont(in.readNBytes(20));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * A file name that is safe to create in a font folder: the name without folders, with anything but ASCII
     * letters and digits, dot, minus, underscore, plus and space replaced (the helper for all users checks the
     * same in the C locale); empty when nothing usable is left or the ending
     * is not a font ending.
     */
    public static Optional<String> safeName(String name) {
        if (name == null) return Optional.empty();
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String cleaned = base.replaceAll("[^A-Za-z0-9._+ -]", "_").strip();
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        if (cleaned.isEmpty() || cleaned.length() > 200) return Optional.empty();
        return hasFontExtension(Path.of(cleaned)) ? Optional.of(cleaned) : Optional.empty();
    }
}
