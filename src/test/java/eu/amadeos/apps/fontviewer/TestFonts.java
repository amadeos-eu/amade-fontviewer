package eu.amadeos.apps.fontviewer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The test font (Caladea Regular, SIL OFL 1.1) and a builder for small hand-made font files. */
public final class TestFonts {
    private TestFonts() {
    }

    /** Copies Caladea-Regular.ttf into the folder (under the given name) and returns its path. */
    public static Path caladea(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        try (InputStream in = TestFonts.class.getResourceAsStream("/fonts/Caladea-Regular.ttf")) {
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    public static Path caladea(Path dir) throws IOException {
        return caladea(dir, "Caladea-Regular.ttf");
    }

    /** Builds an sfnt file from tables; enough for the table reader, not for Java's font engine. */
    public static final class Sfnt {
        private final Map<String, byte[]> tables = new LinkedHashMap<>();
        private int version = 0x00010000;

        public Sfnt version(int v) {
            version = v;
            return this;
        }

        public Sfnt table(String tag, byte[] data) {
            tables.put(tag, data);
            return this;
        }

        /** A name table with Windows English records. */
        public Sfnt names(Map<Integer, String> names) {
            ByteArrayOutputStream strings = new ByteArrayOutputStream();
            ByteBuffer head = ByteBuffer.allocate(6 + 12 * names.size());
            head.putShort((short) 0).putShort((short) names.size()).putShort((short) (6 + 12 * names.size()));
            for (Map.Entry<Integer, String> e : names.entrySet()) {
                byte[] b = e.getValue().getBytes(StandardCharsets.UTF_16BE);
                head.putShort((short) 3).putShort((short) 1).putShort((short) 0x409).putShort(e.getKey().shortValue())
                        .putShort((short) b.length).putShort((short) strings.size());
                strings.writeBytes(b);
            }
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            all.writeBytes(head.array());
            all.writeBytes(strings.toByteArray());
            return table("name", all.toByteArray());
        }

        /** A cmap with one format 12 subtable (3, 10): groups of {start, end, firstGlyph}. */
        public Sfnt cmap12(int[]... groups) {
            ByteBuffer sub = ByteBuffer.allocate(16 + 12 * groups.length);
            sub.putShort((short) 12).putShort((short) 0).putInt(16 + 12 * groups.length).putInt(0).putInt(groups.length);
            for (int[] g : groups) sub.putInt(g[0]).putInt(g[1]).putInt(g[2]);
            ByteBuffer cmap = ByteBuffer.allocate(4 + 8 + sub.capacity());
            cmap.putShort((short) 0).putShort((short) 1).putShort((short) 3).putShort((short) 10).putInt(12);
            cmap.put(sub.array());
            return table("cmap", cmap.array());
        }

        /** An OS/2 table with these PANOSE family and serif values. */
        public Sfnt os2(int family, int serif) {
            byte[] b = new byte[96];
            b[32] = (byte) family;
            b[33] = (byte) serif;
            return table("OS/2", b);
        }

        public Sfnt maxp(int glyphs) {
            ByteBuffer b = ByteBuffer.allocate(6).putInt(0x00005000).putShort((short) glyphs);
            return table("maxp", b.array());
        }

        public byte[] bytes() {
            return face(0);
        }

        /** The font with its tables placed after {@code base} bytes (for collections). */
        byte[] face(int base) {
            int n = tables.size();
            int dir = 12 + 16 * n;
            ByteBuffer out = ByteBuffer.allocate(dir + tables.values().stream().mapToInt(t -> (t.length + 3) & ~3).sum());
            out.putInt(version).putShort((short) n).putShort((short) 0).putShort((short) 0).putShort((short) 0);
            int offset = base + dir;
            for (Map.Entry<String, byte[]> e : tables.entrySet()) {
                out.put(e.getKey().getBytes(StandardCharsets.ISO_8859_1)).putInt(0).putInt(offset)
                        .putInt(e.getValue().length);
                offset += (e.getValue().length + 3) & ~3;
            }
            for (byte[] t : tables.values()) {
                out.put(t);
                out.position((out.position() + 3) & ~3);
            }
            return out.array();
        }

        /** A collection of this font twice (faces 0 and 1 the same). */
        public byte[] collectionOfTwo() {
            int header = 12 + 8;
            byte[] first = face(header);
            byte[] second = face(header + first.length);
            ByteBuffer out = ByteBuffer.allocate(header + first.length + second.length);
            out.put("ttcf".getBytes(StandardCharsets.ISO_8859_1)).putInt(0x00010000).putInt(2)
                    .putInt(header).putInt(header + first.length);
            out.put(first).put(second);
            return out.array();
        }
    }
}
