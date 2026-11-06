package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.Outlines;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontTablesTest {
    @TempDir
    Path dir;

    @Test
    void readsARealFont() throws Exception {
        FontTables t = FontTables.read(TestFonts.caladea(dir), 0);
        FontDetails d = t.details();
        assertEquals("Caladea", d.family());
        assertEquals("Regular", d.style());
        assertEquals("1.001", d.shortVersion());
        assertTrue(d.designer().contains("Giovagnoli"));
        assertEquals("SIL Open Font License 1.1", d.shortLicense());
        assertEquals(Outlines.TRUETYPE, d.outlines());
        assertTrue(d.kerning());
        assertFalse(d.variable());
        assertEquals(2, d.panoseFamily());
        assertTrue(d.glyphs() > 400);
        Coverage c = t.coverage();
        assertEquals(434, c.size());
        assertTrue(c.contains('ß'));
        assertTrue(c.contains('€'));
        assertFalse(c.contains(0x3B1)); // no Greek
    }

    @Test
    void readsHandMadeTablesWithFormat12() throws Exception {
        Map<Integer, String> names = new TreeMap<>(Map.of(1, "Tiny", 2, "Bold", 5, "Version 2.5", 9, "A. Designer",
                13, "Licensed under the Apache License, Version 2.0", 16, "Tiny Family", 17, "Heavy"));
        byte[] font = new TestFonts.Sfnt().names(names).os2(2, 11).maxp(7)
                .cmap12(new int[]{0x41, 0x43, 1}, new int[]{0x1F600, 0x1F601, 10}).table("CFF ", new byte[8]).bytes();
        FontTables t = new FontTables(ByteBuffer.wrap(font), 0);
        FontDetails d = t.details();
        assertEquals("Tiny Family", d.family());
        assertEquals("Heavy", d.style());
        assertEquals("2.5", d.shortVersion());
        assertEquals("A. Designer", d.designer());
        assertEquals("Apache License 2.0", d.shortLicense());
        assertEquals(Outlines.POSTSCRIPT, d.outlines());
        assertEquals(7, d.glyphs());
        assertEquals(2, d.panoseFamily());
        assertEquals(11, d.panoseSerif());
        assertFalse(d.kerning());
        assertEquals(5, t.coverage().size());
        assertTrue(t.coverage().contains(0x1F601));
        assertEquals("", t.name(10));
    }

    @Test
    void glyphZeroIsNotACharacter() throws Exception {
        byte[] font = new TestFonts.Sfnt().cmap12(new int[]{0x20, 0x22, 0}).bytes();
        Coverage c = new FontTables(ByteBuffer.wrap(font), 0).coverage();
        assertEquals(2, c.size());
        assertFalse(c.contains(0x20));
    }

    @Test
    void readsFacesOfACollection() throws Exception {
        byte[] ttc = new TestFonts.Sfnt().names(Map.of(1, "Coll")).maxp(3).collectionOfTwo();
        Path file = dir.resolve("c.ttc");
        Files.write(file, ttc);
        assertEquals("Coll", FontTables.read(file, 1).details().family());
        assertEquals(3, FontTables.read(file, 0).details().glyphs());
        assertThrows(IOException.class, () -> FontTables.read(file, 2));
    }

    @Test
    void refusesWhatIsNoFont() throws Exception {
        Path text = dir.resolve("x.ttf");
        Files.writeString(text, "this is not a font at all, only text");
        assertThrows(IOException.class, () -> FontTables.read(text, 0));
        assertTrue(FontTables.detailsOf(text, 0).isEmpty());
        Path tiny = dir.resolve("t.ttf");
        Files.write(tiny, new byte[]{0, 1, 0, 0, 0});
        assertThrows(IOException.class, () -> FontTables.read(tiny, 0));
    }

    @Test
    void survivesTablesThatPointOutside() throws Exception {
        byte[] font = new TestFonts.Sfnt().names(Map.of(1, "Broken")).bytes();
        // the name table claims to be much longer than the file
        ByteBuffer b = ByteBuffer.wrap(font);
        b.putInt(12 + 12, 1_000_000);
        FontTables t = new FontTables(b, 0);
        assertEquals("", t.name(1));
        assertEquals(Coverage.EMPTY, t.coverage());
        assertEquals(Outlines.UNKNOWN, t.outlines());
    }
}
