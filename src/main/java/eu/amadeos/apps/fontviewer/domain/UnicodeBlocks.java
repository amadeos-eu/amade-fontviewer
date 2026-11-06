package eu.amadeos.apps.fontviewer.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Unicode blocks with their ranges, worked out once from {@link Character.UnicodeBlock}. */
public final class UnicodeBlocks {
    /**
     * A block such as Latin-1 Supplement, U+0080 to U+00FF.
     *
     * @param id Java's name, {@code LATIN_1_SUPPLEMENT}
     */
    public record Block(String id, int start, int end) {
        /** "Latin 1 Supplement" */
        public String displayName() {
            StringBuilder b = new StringBuilder();
            for (String word : id.split("_")) {
                if (!b.isEmpty()) b.append(' ');
                b.append(word.length() <= 1 || word.equals("CJK") || word.equals("IPA")
                        ? word : word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT));
            }
            return b.toString();
        }

        public int length() {
            return end - start + 1;
        }
    }

    private static volatile List<Block> blocks;

    private UnicodeBlocks() {
    }

    /** All blocks except the surrogates and the private use areas, in Unicode order. */
    public static List<Block> all() {
        List<Block> result = blocks;
        if (result == null) {
            synchronized (UnicodeBlocks.class) {
                if (blocks == null) blocks = compute();
                result = blocks;
            }
        }
        return result;
    }

    /** The block the character is in. */
    public static java.util.Optional<Block> of(int codePoint) {
        for (Block b : all()) if (codePoint >= b.start() && codePoint <= b.end()) return java.util.Optional.of(b);
        return java.util.Optional.empty();
    }

    private static List<Block> compute() {
        List<Block> list = new ArrayList<>();
        Character.UnicodeBlock current = null;
        int start = 0;
        for (int cp = 0; cp <= Character.MAX_CODE_POINT + 1; cp++) {
            Character.UnicodeBlock block = cp <= Character.MAX_CODE_POINT ? Character.UnicodeBlock.of(cp) : null;
            if (block != current) {
                if (current != null && keep(current)) list.add(new Block(current.toString(), start, cp - 1));
                current = block;
                start = cp;
            }
        }
        return List.copyOf(list);
    }

    private static boolean keep(Character.UnicodeBlock block) {
        String name = block.toString();
        return !name.contains("SURROGATES") && !name.contains("PRIVATE_USE") && !name.equals("SPECIALS")
                && !name.equals("TAGS");
    }
}
