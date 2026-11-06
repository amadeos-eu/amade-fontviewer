package eu.amadeos.apps.fontviewer.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/** The characters a font has a glyph for, as sorted Unicode code points (from its {@code cmap} table). */
public final class Coverage {
    public static final Coverage EMPTY = new Coverage(new int[0]);

    private final int[] codePoints;

    private Coverage(int[] sorted) {
        this.codePoints = sorted;
    }

    /** Sorts the code points and drops duplicates and control characters. */
    public static Coverage of(int[] codePoints) {
        return new Coverage(IntStream.of(codePoints).filter(cp -> cp >= 0x20 && cp <= Character.MAX_CODE_POINT
                && !(cp >= 0x7F && cp < 0xA0) && Character.getType(cp) != Character.SURROGATE)
                .sorted().distinct().toArray());
    }

    public boolean contains(int codePoint) {
        return Arrays.binarySearch(codePoints, codePoint) >= 0;
    }

    public int size() {
        return codePoints.length;
    }

    public boolean isEmpty() {
        return codePoints.length == 0;
    }

    public IntStream stream() {
        return IntStream.of(codePoints);
    }

    /** The code points of the font inside the block. */
    public int[] in(UnicodeBlocks.Block block) {
        int from = lowerBound(block.start());
        int to = lowerBound(block.end() + 1);
        return Arrays.copyOfRange(codePoints, from, to);
    }

    /** The blocks the font has at least one character of, in Unicode order. */
    public List<UnicodeBlocks.Block> blocks() {
        return UnicodeBlocks.all().stream().filter(b -> lowerBound(b.end() + 1) > lowerBound(b.start())).toList();
    }

    private int lowerBound(int value) {
        int i = Arrays.binarySearch(codePoints, value);
        return i >= 0 ? i : -i - 1;
    }
}
