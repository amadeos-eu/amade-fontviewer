package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.Outlines;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Reads what a TrueType or OpenType file (or one face of a collection) says about itself: the texts of the
 * {@code name} table, PANOSE from {@code OS/2}, the number of glyphs from {@code maxp}, the outline kind and the
 * characters of the {@code cmap} table. Only reads; a broken file gives an {@link IOException}, never a crash.
 */
public final class FontTables {
    // name IDs, see the OpenType specification of the name table
    static final int COPYRIGHT = 0, FAMILY = 1, STYLE = 2, TYPO_FAMILY = 16, TYPO_STYLE = 17, FULL_NAME = 4, VERSION = 5, MANUFACTURER = 8, DESIGNER = 9,
            DESCRIPTION = 10, VENDOR_URL = 11, DESIGNER_URL = 12, LICENSE = 13, LICENSE_URL = 14;

    private final ByteBuffer data;
    private final Map<String, int[]> tables = new HashMap<>();

    // --8<-- [start:directory]
    /** Parses the table directory of face {@code index} (0 for files that are not collections). */
    public FontTables(ByteBuffer data, int index) throws IOException {
        this.data = data.duplicate();
        try {
            int base = 0;
            if (tag(0).equals("ttcf")) {
                int count = u32(8);
                if (index < 0 || index >= count) throw new IOException("the collection has no face " + index);
                base = u32(12 + 4 * index);
            }
            int version = u32(base);
            String versionTag = tag(base);
            if (version != 0x00010000 && !versionTag.equals("OTTO") && !versionTag.equals("true")) {
                throw new IOException("not a TrueType or OpenType font");
            }
            int count = u16(base + 4);
            for (int i = 0; i < count; i++) {
                int record = base + 12 + 16 * i;
                int offset = u32(record + 8);
                int length = u32(record + 12);
                if (offset < 0 || length < 0 || (long) offset + length > this.data.limit()) continue;
                tables.put(tag(record), new int[]{offset, length});
            }
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IOException("broken font file", e);
        }
    }
    // --8<-- [end:directory]

    /** Opens the file and reads face {@code index}. */
    public static FontTables read(Path file, int index) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size > 256L * 1024 * 1024) throw new IOException("font file too large");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // read on
            }
            buffer.flip();
            return new FontTables(buffer, index);
        }
    }

    public boolean has(String tag) {
        return tables.containsKey(tag);
    }

    public Outlines outlines() {
        if (has("glyf")) return Outlines.TRUETYPE;
        if (has("CFF ") || has("CFF2")) return Outlines.POSTSCRIPT;
        return Outlines.UNKNOWN;
    }

    public FontDetails details() {
        int[] panose = panose();
        String designer = name(DESIGNER);
        return new FontDetails(name(FULL_NAME), name(VERSION), designer, name(MANUFACTURER), name(LICENSE),
                name(LICENSE_URL), name(COPYRIGHT), first(name(DESIGNER_URL), name(VENDOR_URL)),
                name(DESCRIPTION), glyphs(), outlines(), has("kern") || has("GPOS"), has("fvar"),
                panose[0], panose[1], first(name(TYPO_FAMILY), name(FAMILY)), first(name(TYPO_STYLE), name(STYLE)));
    }

    private static String first(String a, String b) {
        return a.isBlank() ? b : a;
    }

    /** bFamilyType and bSerifStyle, or zeros. */
    int[] panose() {
        int[] t = tables.get("OS/2");
        if (t == null || t[1] < 44) return new int[]{0, 0};
        return new int[]{u8(t[0] + 32), u8(t[0] + 33)};
    }

    int glyphs() {
        int[] t = tables.get("maxp");
        return t == null || t[1] < 6 ? 0 : u16(t[0] + 4);
    }

    // --8<-- [start:name]
    /**
     * The text with this name ID: Windows English first, then any Windows language, then Unicode, then
     * Macintosh Roman; empty when the file has none.
     */
    public String name(int nameId) {
        int[] t = tables.get("name");
        if (t == null) return "";
        try {
            int count = u16(t[0] + 2);
            int strings = t[0] + u16(t[0] + 4);
            String best = null;
            int bestRank = Integer.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                int r = t[0] + 6 + 12 * i;
                if (u16(r + 6) != nameId) continue;
                int platform = u16(r), encoding = u16(r + 2), language = u16(r + 4);
                int rank;
                if (platform == 3 && (encoding == 1 || encoding == 10) && language == 0x409) rank = 0;
                else if (platform == 3 && (encoding == 1 || encoding == 10)) rank = 1;
                else if (platform == 0) rank = 2;
                else if (platform == 1 && encoding == 0 && language == 0) rank = 3;
                else continue;
                if (rank >= bestRank) continue;
                int length = u16(r + 8);
                int offset = strings + u16(r + 10);
                if (offset + length > data.limit()) continue;
                byte[] bytes = new byte[length];
                data.get(offset, bytes);
                best = new String(bytes, rank == 3 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_16BE);
                bestRank = rank;
            }
            return best == null ? "" : best;
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }
    // --8<-- [end:name]

    // --8<-- [start:coverage]
    /** The characters with a glyph, from the best Unicode subtable of {@code cmap}. */
    public Coverage coverage() {
        int[] t = tables.get("cmap");
        if (t == null) return Coverage.EMPTY;
        try {
            int count = u16(t[0] + 2);
            int format12 = -1, format4 = -1, symbol = -1;
            for (int i = 0; i < count; i++) {
                int r = t[0] + 4 + 8 * i;
                int platform = u16(r), encoding = u16(r + 2);
                int sub = t[0] + u32(r + 4);
                int format = u16(sub);
                boolean unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10));
                if (unicode && format == 12 && format12 < 0) format12 = sub;
                if (unicode && format == 4 && format4 < 0) format4 = sub;
                if (platform == 3 && encoding == 0 && format == 4 && symbol < 0) symbol = sub;
            }
            if (format12 >= 0) return Coverage.of(format12(format12));
            if (format4 >= 0) return Coverage.of(format4(format4));
            if (symbol >= 0) return Coverage.of(format4(symbol));
            return Coverage.EMPTY;
        } catch (IndexOutOfBoundsException e) {
            return Coverage.EMPTY;
        }
    }
    // --8<-- [end:coverage]

    private int[] format12(int sub) {
        int groups = u32(sub + 12);
        IntStream.Builder out = IntStream.builder();
        int total = 0;
        for (int g = 0; g < groups && total <= Character.MAX_CODE_POINT; g++) {
            int r = sub + 16 + 12 * g;
            int start = u32(r), end = Math.min(u32(r + 4), Character.MAX_CODE_POINT), glyph = u32(r + 8);
            for (int cp = start; cp <= end && cp >= 0; cp++, total++) {
                if (glyph + (cp - start) != 0) out.add(cp);
            }
        }
        return out.build().toArray();
    }

    private int[] format4(int sub) {
        int segments = u16(sub + 6) / 2;
        int ends = sub + 14;
        int starts = ends + 2 * segments + 2;
        int deltas = starts + 2 * segments;
        int ranges = deltas + 2 * segments;
        IntStream.Builder out = IntStream.builder();
        for (int s = 0; s < segments; s++) {
            int end = u16(ends + 2 * s), start = u16(starts + 2 * s);
            int delta = (short) u16(deltas + 2 * s);
            int rangeAt = ranges + 2 * s;
            int range = u16(rangeAt);
            for (int cp = start; cp <= end && cp != 0xFFFF; cp++) {
                int glyph;
                if (range == 0) {
                    glyph = (cp + delta) & 0xFFFF;
                } else {
                    int at = rangeAt + range + 2 * (cp - start);
                    if (at + 2 > data.limit()) break;
                    glyph = u16(at);
                    if (glyph != 0) glyph = (glyph + delta) & 0xFFFF;
                }
                if (glyph != 0) out.add(cp);
            }
        }
        return out.build().toArray();
    }

    private String tag(int at) {
        byte[] b = new byte[4];
        data.get(at, b);
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private int u8(int at) {
        return data.get(at) & 0xFF;
    }

    private int u16(int at) {
        return data.getShort(at) & 0xFFFF;
    }

    private int u32(int at) {
        return data.getInt(at);
    }

    /** Reads only the details, for callers that do not keep the tables. */
    public static Optional<FontDetails> detailsOf(Path file, int index) {
        try {
            return Optional.of(read(file, index).details());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
