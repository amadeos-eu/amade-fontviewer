package eu.amadeos.apps.fontviewer.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {
    static FontFace face(String family, String style, int weight, int slant, FontScope scope) {
        return new FontFace(Path.of("/f/" + family + "-" + style + ".ttf"), 0, family, style, weight, slant, false,
                "TrueType", Set.of("de"), scope);
    }

    @Test
    void type1Files() {
        assertTrue(FontFiles.isType1(Path.of("/usr/share/fonts/type1/urw-base35/NimbusSans-Regular.t1")));
        assertTrue(FontFiles.isType1(Path.of("a.PFB")));
        assertTrue(FontFiles.isType1(Path.of("a.pfa")));
        assertFalse(FontFiles.isType1(Path.of("a.otf")));
        assertFalse(FontFiles.isType1(Path.of("t1")));
    }

    @Test
    void categoryFromPanose() {
        assertEquals(Category.SERIF, Category.of("Caladea", 2, 2, false));
        assertEquals(Category.SANS_SERIF, Category.of("Carlito", 2, 11, false));
        assertEquals(Category.HANDWRITING, Category.of("Pacifico", 3, 0, false));
        assertEquals(Category.DISPLAY, Category.of("Lobster", 4, 0, false));
        assertEquals(Category.SYMBOLS, Category.of("Webdings", 5, 0, false));
    }

    @Test
    void categoryFromNameAndWidth() {
        assertEquals(Category.MONOSPACE, Category.of("Anything", 2, 2, true));
        assertEquals(Category.MONOSPACE, Category.of("JetBrains Mono", 0, 0, false));
        assertEquals(Category.MONOSPACE, Category.of("Courier 10 Pitch", 0, 0, false));
        assertEquals(Category.SYMBOLS, Category.of("Noto Color Emoji", 0, 0, false));
        assertEquals(Category.SANS_SERIF, Category.of("DejaVu Sans", 2, 2, false));
        assertEquals(Category.SERIF, Category.of("DejaVu Serif", 0, 0, false));
        assertEquals(Category.SANS_SERIF, Category.of("Inter", 0, 0, false));
        assertEquals(Category.SERIF, Category.of("Lora", 0, 0, false, () -> true), "no PANOSE: the letters decide");
        assertEquals(Category.SERIF, Category.of("Lora", 2, 0, false, () -> true));
        assertEquals(Category.SANS_SERIF, Category.of("Carlito", 2, 11, false, () -> true), "PANOSE first");
        assertEquals("category.sans-serif", Category.SANS_SERIF.textKey());
    }

    @Test
    void familySortsStylesAndFindsRegular() {
        FontFamily f = new FontFamily("Inter", List.of(face("Inter", "Bold Italic", 200, 100, FontScope.SYSTEM),
                face("Inter", "Bold", 200, 0, FontScope.SYSTEM), face("Inter", "Light", 50, 0, FontScope.SYSTEM),
                face("Inter", "Regular", 80, 0, FontScope.SYSTEM)), Category.SANS_SERIF);
        assertEquals(List.of("Light", "Regular", "Bold", "Bold Italic"), f.faces().stream().map(FontFace::style).toList());
        assertEquals("Regular", f.regular().style());
        assertEquals("Bold", f.face("Bold").style());
        assertEquals("Regular", f.face("Nonexistent").style());
        assertEquals(FontScope.SYSTEM, f.scope());
        assertFalse(f.mine());
    }

    @Test
    void familyWithoutRegularPicksNearestUpright() {
        FontFamily f = new FontFamily("X", List.of(face("X", "Italic", 80, 100, FontScope.SYSTEM),
                face("X", "Medium", 100, 0, FontScope.SYSTEM)), Category.SERIF);
        assertEquals("Medium", f.regular().style());
    }

    @Test
    void familyIsMineWhenOneStyleIs() {
        FontFamily f = new FontFamily("Caladea", List.of(face("Caladea", "Regular", 80, 0, FontScope.USER),
                face("Caladea", "Bold", 200, 0, FontScope.SYSTEM)), Category.SERIF);
        assertTrue(f.mine());
        assertEquals(FontScope.USER, f.scope());
        assertThrows(IllegalArgumentException.class, () -> new FontFamily("Empty", List.of(), Category.SERIF));
    }

    @Test
    void faceNames() {
        FontFace regular = face("Inter", "Regular", 80, 0, FontScope.SYSTEM);
        assertEquals("Inter", regular.fullName());
        assertEquals("Inter Bold", face("Inter", "Bold", 200, 0, FontScope.SYSTEM).fullName());
        assertFalse(regular.italic());
        assertTrue(face("Inter", "Italic", 80, 100, FontScope.SYSTEM).italic());
        FontFace blank = new FontFace(Path.of("/a.ttf"), 0, "A", " ", 80, 0, false, null, null, FontScope.SYSTEM);
        assertEquals("Regular", blank.style());
        assertTrue(blank.drawable());
        assertFalse(new FontFace(Path.of("/a.pcf"), 0, "A", "R", 80, 0, false, "PCF", null, FontScope.SYSTEM).drawable());
    }

    @Test
    void fontFileEndings() {
        assertTrue(FontFiles.hasFontExtension(Path.of("/x/Font.TTF")));
        assertTrue(FontFiles.hasFontExtension(Path.of("a.otf")));
        assertTrue(FontFiles.hasFontExtension(Path.of("a.ttc")));
        assertTrue(FontFiles.hasFontExtension(Path.of("a.pfb")));
        assertFalse(FontFiles.hasFontExtension(Path.of("a.woff2")));
        assertFalse(FontFiles.hasFontExtension(Path.of("ttf")));
        assertEquals(Optional.of("otf"), FontFiles.extension(Path.of("x.OTF")));
    }

    @Test
    void fontFileMagic() {
        assertTrue(FontFiles.looksLikeFont(new byte[]{0, 1, 0, 0, 9}));
        assertTrue(FontFiles.looksLikeFont("OTTO....".getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(FontFiles.looksLikeFont("ttcf....".getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(FontFiles.looksLikeFont("true....".getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(FontFiles.looksLikeFont(new byte[]{(byte) 0x80, 1, 5, 0}));
        assertTrue(FontFiles.looksLikeFont("%!PS-AdobeFont-1.0: X".getBytes(StandardCharsets.ISO_8859_1)));
        assertFalse(FontFiles.looksLikeFont("root:x:0:0:root".getBytes(StandardCharsets.ISO_8859_1)));
        assertFalse(FontFiles.looksLikeFont(new byte[]{0, 1}));
        assertFalse(FontFiles.looksLikeFont(Path.of("/does/not/exist.ttf")));
    }

    @Test
    void safeNames() {
        assertEquals(Optional.of("Caladea-Regular.ttf"), FontFiles.safeName("Caladea-Regular.ttf"));
        assertEquals(Optional.of("evil.ttf"), FontFiles.safeName("../../etc/evil.ttf"));
        assertEquals(Optional.of("x.ttf"), FontFiles.safeName("..x.ttf"));
        assertEquals(Optional.of("M_nchen Sans.otf"), FontFiles.safeName("München Sans.otf"));
        assertEquals(Optional.of("a_b.ttf"), FontFiles.safeName("a;b.ttf"));
        assertEquals(Optional.empty(), FontFiles.safeName("shadow"));
        assertEquals(Optional.empty(), FontFiles.safeName("x.sh"));
        assertEquals(Optional.empty(), FontFiles.safeName(null));
        assertEquals(Optional.empty(), FontFiles.safeName("a".repeat(300) + ".ttf"));
    }

    @Test
    void coverageAndBlocks() {
        Coverage c = Coverage.of(new int[]{0x41, 0x42, 0x41, 0xDF, 0x0A, 0x85, 0x20AC, 0x1F600});
        assertEquals(5, c.size());
        assertTrue(c.contains(0xDF));
        assertFalse(c.contains(0x0A));
        assertFalse(c.contains(0x85));
        List<String> blocks = c.blocks().stream().map(UnicodeBlocks.Block::id).toList();
        assertEquals(List.of("BASIC_LATIN", "LATIN_1_SUPPLEMENT", "CURRENCY_SYMBOLS", "EMOTICONS"), blocks);
        UnicodeBlocks.Block latin1 = c.blocks().get(1);
        assertEquals(0x80, latin1.start());
        assertEquals(0xFF, latin1.end());
        assertEquals(128, latin1.length());
        assertEquals("Latin 1 Supplement", latin1.displayName());
        assertEquals(1, c.in(latin1).length);
        assertEquals(2, c.in(c.blocks().getFirst()).length);
        assertTrue(Coverage.EMPTY.isEmpty());
        assertEquals("CJK Unified Ideographs", UnicodeBlocks.of(0x4E00).orElseThrow().displayName());
        assertTrue(UnicodeBlocks.all().stream().noneMatch(b -> b.id().contains("SURROGATES")));
        assertTrue(UnicodeBlocks.all().stream().noneMatch(b -> b.id().contains("PRIVATE_USE")));
    }

    @Test
    void languageNames() {
        List<String> names = LanguageNames.names(Set.of("zu", "de", "en", "pl", "xx-unknown", "pt-br"), Locale.ENGLISH);
        assertEquals("German", names.get(0));
        assertEquals("English", names.get(1));
        assertEquals("Polish", names.get(2));
        assertTrue(names.contains("Zulu"));
        assertTrue(names.contains("Portuguese (Brazil)"));
        assertEquals(List.of("Deutsch"), LanguageNames.names(Set.of("de"), Locale.GERMAN));
        assertEquals(List.of(), LanguageNames.names(Set.of(), Locale.ENGLISH));
    }

    @Test
    void detailsTexts() {
        FontDetails d = new FontDetails("Caladea", "Version 1.001; ttfautohint (v1.8)", "C. G.", "HT",
                "This Font Software is licensed under the SIL Open Font License, Version 1.1.", "", "© 2012",
                "", "", 464, Outlines.TRUETYPE, true, false, 2, 2, "Caladea", "Regular");
        assertEquals("1.001", d.shortVersion());
        assertEquals("SIL Open Font License 1.1", d.shortLicense());
        assertTrue(d.freeLicense());
        FontDetails apache = new FontDetails("", "2.0", "", "", "", "http://www.apache.org/licenses/LICENSE-2.0",
                "", "", "", 0, null, false, false, 0, 0, null, null);
        assertEquals("Apache License 2.0", apache.shortLicense());
        assertEquals(Outlines.UNKNOWN, apache.outlines());
        assertEquals("", apache.family());
        FontDetails other = new FontDetails("", "", "", "", "All rights reserved by Foundry X.\nSecond line", "",
                "", "", "", 0, Outlines.POSTSCRIPT, false, false, 0, 0, "", "");
        assertEquals("All rights reserved by Foundry X.", other.shortLicense());
        assertFalse(other.freeLicense());
        assertEquals("", FontDetails.NONE.shortLicense());
        assertEquals("outlines.postscript", Outlines.POSTSCRIPT.textKey());
    }

    @Test
    void sampleKeys() {
        assertEquals("sample.own", SampleText.OWN.textKey());
        assertTrue(SampleText.ALPHABET_TEXT.contains("ß"));
    }
}
