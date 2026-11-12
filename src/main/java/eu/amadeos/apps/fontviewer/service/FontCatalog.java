package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.domain.Category;
import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.FontScope;
import eu.amadeos.commons.process.CommandRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Collator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * The installed fonts as fontconfig knows them ({@code fc-list}), grouped into families and sorted by name.
 * The kind of each family (serif, sans serif ...) comes from the PANOSE data of its regular style.
 */
public class FontCatalog {
    /** One line per face; fields separated by tabs. */
    // --8<-- [start:format]
    static final String FORMAT = "%{file}\\t%{index}\\t%{family}\\t%{style}\\t%{weight}\\t%{slant}\\t%{spacing}"
            + "\\t%{fontformat}\\t%{variable}\\t%{lang}\\n";
    // --8<-- [end:format]

    private final CommandRunner runner;
    private final FontPlaces places;
    private final BiFunction<Path, Integer, FontDetails> details;
    private final BiPredicate<Path, Integer> serifShape;

    public FontCatalog(CommandRunner runner, FontPlaces places) {
        this(runner, places, (file, index) -> FontTables.detailsOf(file, index).orElse(FontDetails.NONE),
                SerifShape::hasSerifs);
    }

    FontCatalog(CommandRunner runner, FontPlaces places, BiFunction<Path, Integer, FontDetails> details) {
        this(runner, places, details, (file, index) -> false);
    }

    FontCatalog(CommandRunner runner, FontPlaces places, BiFunction<Path, Integer, FontDetails> details,
                BiPredicate<Path, Integer> serifShape) {
        this.runner = runner;
        this.places = places;
        this.details = details;
        this.serifShape = serifShape;
    }

    /** Asks fontconfig; slow enough (a second or two with many fonts) to run in the background. */
    public List<FontFamily> load() throws IOException {
        String output = runner.output(List.of("fc-list", "--format=" + FORMAT), Duration.ofSeconds(60));
        return families(parse(output, places), details, serifShape);
    }

    /** The faces of the fc-list output; broken lines, named instances of variable fonts and duplicates dropped. */
    static List<FontFace> parse(String output, FontPlaces places) {
        Map<String, FontFace> faces = new LinkedHashMap<>();
        for (String line : output.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 10 || f[0].isBlank() || f[2].isBlank()) continue;
            int index = number(f[1], 0);
            // Named instances of a variable font (index above 0xFFFF) look like the default one in Java.
            if (index > 0xFFFF) continue;
            Path file = Path.of(f[0]);
            String family = firstOf(f[2]);
            String style = firstOf(f[3]);
            boolean variable = f[8].equalsIgnoreCase("True");
            if (variable && style.isBlank()) style = "Regular";
            Set<String> languages = f[9].isBlank() ? Set.of() : Set.of(f[9].split("\\|"));
            FontFace face = new FontFace(file, index, family, style, number(f[4], FontFace.REGULAR),
                    number(f[5], 0), number(f[6], 0) >= 100, f[7], languages, places.scopeOf(file));
            faces.putIfAbsent(file + "#" + index, face);
        }
        return List.copyOf(faces.values());
    }

    /** Groups faces by family, works out each family's kind and sorts by name (as people sort, not by code). */
    static List<FontFamily> families(List<FontFace> faces, BiFunction<Path, Integer, FontDetails> details) {
        return families(faces, details, (file, index) -> false);
    }

    static List<FontFamily> families(List<FontFace> faces, BiFunction<Path, Integer, FontDetails> details,
                                     BiPredicate<Path, Integer> serifShape) {
        Map<String, List<FontFace>> byName = faces.stream().collect(Collectors.groupingBy(FontFace::family,
                LinkedHashMap::new, Collectors.toList()));
        List<FontFamily> result = new ArrayList<>();
        for (Map.Entry<String, List<FontFace>> e : byName.entrySet()) {
            FontFamily family = new FontFamily(e.getKey(), dropSameStyle(e.getValue()), Category.SANS_SERIF);
            FontFace regular = family.regular();
            FontDetails d = details.apply(regular.file(), regular.index());
            boolean mono = family.faces().stream().allMatch(FontFace::monospace);
            result.add(family.withCategory(Category.of(family.name(), d.panoseFamily(), d.panoseSerif(), mono,
                    () -> serifShape.test(regular.file(), regular.index()))));
        }
        Collator collator = Collator.getInstance(Locale.ROOT);
        collator.setStrength(Collator.SECONDARY);
        result.sort((a, b) -> collator.compare(a.name(), b.name()));
        return List.copyOf(result);
    }

    /**
     * The same style twice (a font installed by the user and by a package, or as .ttf and .otf) is shown once;
     * the user's own copy wins, as it does in fontconfig. Among copies in the same place the best format wins:
     * OpenType or TrueType before Type 1 (Ghostscript's URW fonts come as .otf, .t1 and .pfb), bitmaps last.
     */
    private static List<FontFace> dropSameStyle(List<FontFace> faces) {
        Map<String, FontFace> byStyle = new LinkedHashMap<>();
        faces.stream().sorted(Comparator.comparing(FontFace::scope).thenComparingInt(FontCatalog::formatRank))
                .forEach(face -> byStyle.putIfAbsent(face.style().toLowerCase(Locale.ROOT), face));
        return List.copyOf(byStyle.values());
    }

    /** 0 for OpenType and TrueType, 1 for Type 1, 2 for anything else (bitmap fonts). */
    static int formatRank(FontFace face) {
        String format = face.format().toLowerCase(Locale.ROOT);
        if (format.equals("truetype") || format.equals("cff")) return 0;
        if (format.equals("type 1")) return 1;
        return face.drawable() ? 1 : 2;
    }

    private static String firstOf(String commaList) {
        int comma = commaList.indexOf(',');
        return (comma < 0 ? commaList : commaList.substring(0, comma)).strip();
    }

    /** "80", "[50 210]" (a variable font's range: the first number) or "" (the fallback). */
    private static int number(String text, int fallback) {
        String s = text.replace("[", " ").replace("]", " ").strip();
        if (s.isEmpty()) return fallback;
        try {
            return (int) Math.round(Double.parseDouble(Arrays.stream(s.split("\\s+")).findFirst().orElse("")));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
