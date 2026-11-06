package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.FakeRunner;
import eu.amadeos.apps.fontviewer.domain.Category;
import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.FontScope;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontCatalogTest {
    static final FontPlaces PLACES = new FontPlaces(Path.of("/home/anna/.local/share/fonts"),
            Path.of("/home/anna/.fonts"), FontPlaces.ALL_USERS);

    static String line(String file, int index, String family, String style, String weight, String slant,
                       String spacing, String format, String variable, String lang) {
        return String.join("\t", file, Integer.toString(index), family, style, weight, slant, spacing, format,
                variable, lang);
    }

    static final String OUTPUT = String.join("\n",
            line("/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Medium.ttf", 0,
                    "JetBrains Mono,JetBrains Mono Medium", "Medium,Regular", "100", "0", "100", "TrueType", "False", "de|en"),
            line("/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Regular.ttf", 0,
                    "JetBrains Mono", "Regular", "80", "0", "100", "TrueType", "False", "de|en"),
            line("/home/anna/.local/share/fonts/Caladea-Regular.ttf", 0, "Caladea", "Regular", "80", "0", "",
                    "TrueType", "False", "de|en|fr"),
            line("/usr/share/fonts/truetype/crosextra/Caladea-Regular.ttf", 0, "Caladea", "Regular", "80", "0", "",
                    "TrueType", "False", "de|en|fr"),
            line("/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf", 0, "Caladea", "Bold", "200", "0", "",
                    "TrueType", "False", "de"),
            line("/usr/share/fonts/opentype/inter/InterVariable.ttf", 0, "Inter", "", "[100 900]", "0", "",
                    "TrueType", "True", ""),
            line("/usr/share/fonts/opentype/inter/InterVariable.ttf", 0x10000, "Inter", "Thin", "100", "0", "",
                    "TrueType", "False", ""),
            line("/usr/local/share/fonts/amadeos/abc.otf", 0, "abc Display", "Regular", "80", "0", "", "CFF",
                    "False", ""),
            "broken line without tabs",
            line("", 0, "", "", "", "", "", "", "", ""),
            "");

    @Test
    void parsesFaces() {
        List<FontFace> faces = FontCatalog.parse(OUTPUT, PLACES);
        assertEquals(7, faces.size());
        FontFace medium = faces.getFirst();
        assertEquals("JetBrains Mono", medium.family());
        assertEquals("Medium", medium.style());
        assertEquals(100, medium.weight());
        assertTrue(medium.monospace());
        assertEquals(java.util.Set.of("de", "en"), medium.languages());
        FontFace inter = faces.stream().filter(f -> f.family().equals("Inter")).findFirst().orElseThrow();
        assertEquals("Regular", inter.style());
        assertEquals(100, inter.weight());
        assertEquals(FontScope.USER, faces.get(2).scope());
        assertEquals(FontScope.SYSTEM, faces.get(3).scope());
        assertEquals(FontScope.ALL_USERS, faces.getLast().scope());
    }

    @Test
    void groupsIntoSortedFamilies() {
        List<FontFamily> families = FontCatalog.families(FontCatalog.parse(OUTPUT, PLACES),
                (file, index) -> file.toString().contains("Caladea")
                        ? new FontDetails("", "", "", "", "", "", "", "", "", 0, null, false, false, 2, 2, "", "")
                        : FontDetails.NONE);
        assertEquals(List.of("abc Display", "Caladea", "Inter", "JetBrains Mono"),
                families.stream().map(FontFamily::name).toList());
        FontFamily caladea = families.get(1);
        // the user's copy of Regular wins over the package's
        assertEquals(2, caladea.faces().size());
        assertEquals(FontScope.USER, caladea.regular().scope());
        assertTrue(caladea.mine());
        assertEquals(Category.SERIF, caladea.category());
        assertEquals(Category.MONOSPACE, families.get(3).category());
        assertEquals("Regular", families.get(3).regular().style());
        assertEquals(Category.SANS_SERIF, families.get(2).category());
        assertFalse(families.get(2).mine());
    }

    @Test
    void loadAsksFcList() throws Exception {
        FakeRunner runner = new FakeRunner().prints("fc-list", OUTPUT);
        List<FontFamily> families = new FontCatalog(runner, PLACES, (f, i) -> FontDetails.NONE).load();
        assertEquals(4, families.size());
        assertTrue(runner.lines().getFirst().startsWith("fc-list --format=%{file}"));
    }

    @Test
    void lettersDecideWithoutPanose() throws Exception {
        FakeRunner runner = new FakeRunner().prints("fc-list", OUTPUT);
        List<FontFamily> families = new FontCatalog(runner, PLACES, (f, i) -> FontDetails.NONE,
                (f, i) -> f.toString().contains("Inter")).load();
        assertEquals(Category.SERIF, families.get(2).category());
        assertEquals(Category.MONOSPACE, families.get(3).category(), "the name comes first");
    }

    @Test
    void loadFailsWithoutFontconfig() {
        FontCatalog catalog = new FontCatalog(new FakeRunner(), PLACES);
        assertThrows(IOException.class, catalog::load);
    }

    @Test
    void places() {
        assertEquals(FontScope.USER, PLACES.scopeOf(Path.of("/home/anna/.fonts/x.ttf")));
        assertEquals(FontScope.USER, PLACES.scopeOf(Path.of("/home/anna/.local/share/fonts/sub/x.ttf")));
        assertEquals(FontScope.ALL_USERS, PLACES.scopeOf(Path.of("/usr/local/share/fonts/other/x.ttf")));
        assertEquals(FontScope.SYSTEM, PLACES.scopeOf(Path.of("/usr/share/fonts/x.ttf")));
        assertTrue(PLACES.removableForAll(Path.of("/usr/local/share/fonts/amadeos/x.ttf")));
        assertFalse(PLACES.removableForAll(Path.of("/usr/local/share/fonts/other/x.ttf")));
        assertFalse(PLACES.removableForAll(Path.of("/usr/local/share/fonts/amadeos/../other/x.ttf")));
    }
}
