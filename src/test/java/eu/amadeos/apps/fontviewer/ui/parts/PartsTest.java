package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.apps.fontviewer.OnEdt;
import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.bootstrap.FontViewerModule;
import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.Outlines;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class PartsTest {
    @TempDir
    Path dir;

    private final Messages msg = Messages.load(FontViewerModule.class, FontViewerModule.MESSAGES, Locale.ENGLISH);

    @Test
    void searchMatches() {
        Coverage c = Coverage.of(new int[]{'A', 'a', 0xDF, 0x20AC, 0x1E9E});
        assertArrayEquals(new int[]{0xDF, 0x1E9E}, CharactersPanel.matches("sharp s", c));
        assertArrayEquals(new int[]{0xDF}, CharactersPanel.matches("ß", c));
        assertArrayEquals(new int[]{0x20AC}, CharactersPanel.matches("U+20AC", c));
        assertArrayEquals(new int[]{0x20AC}, CharactersPanel.matches("euro", c));
        assertArrayEquals(new int[]{}, CharactersPanel.matches("U+0100", c));
        assertEquals(Optional.of(0xDF), CharactersPanel.codeOf("u+00df"));
        assertEquals(Optional.of(0xDF), CharactersPanel.codeOf("0xDF"));
        assertEquals(Optional.of(0x20AC), CharactersPanel.codeOf("20ac"));
        assertEquals(Optional.empty(), CharactersPanel.codeOf("df"));
        assertEquals(Optional.empty(), CharactersPanel.codeOf("U+XYZ"));
        assertEquals(Optional.empty(), CharactersPanel.codeOf("U+FFFFFFF"));
    }

    @Test
    void detailRows() {
        FontDetails d = new FontDetails("", "Version 3.2", "", "Maker Inc.", "", "", "(c) Maker", "https://maker",
                "A sans.", 10, Outlines.POSTSCRIPT, false, true, 0, 0, "", "");
        List<Detail> rows = new DetailRows(msg).add("details.style", "Bold").add("details.files", " ")
                .font(d, Coverage.EMPTY, Set.of("de", "en", "fr", "es", "it", "nl", "pl", "pt", "cs")).more(d).build();
        assertEquals(List.of("Style", "Version", "Designer", "Languages", "Format", "Description", "Copyright", "Web"),
                rows.stream().map(Detail::label).toList());
        assertEquals("3.2", rows.get(1).value());
        assertEquals("Maker Inc.", rows.get(2).value());
        assertEquals("German, English, French, Spanish, Italian, Dutch and 3 more", rows.get(3).value());
        assertEquals("OpenType with PostScript outlines, variable font (shown in its default style)", rows.get(4).value());
        assertEquals("German, English", new DetailRows(msg).languages(Set.of("en", "de")));
    }

    @Test
    void charactersPanelShowsBlocksAndSearches() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Path file = TestFonts.caladea(dir);
        FontLoader loader = new FontLoader();
        Font font = loader.font(file, 0).orElseThrow();
        Coverage coverage = new FontInspector(loader).inspect(file, 0).coverage();
        OnEdt.run(() -> {
            CharactersPanel p = new CharactersPanel(msg);
            p.show("Caladea", font, coverage);
            assertEquals("Latin 1 Supplement", p.blocks().getSelectedItem().toString());
            assertEquals(96, p.grid().count(), "the whole block, missing ones grey");
            assertTrue(p.summaryText().startsWith("Caladea has 434 characters in 16 Unicode blocks"));
            p.blocks().setSelectedIndex(0);
            assertEquals(434, p.grid().count());
            p.findField().setText("sharp s");
            assertEquals(1, p.grid().count());
            assertEquals(0xDF, p.grid().selectedCodePoint());
            assertTrue(p.codeText().contains("U+00DF"));
            assertTrue(p.codeText().contains("LATIN SMALL LETTER SHARP S"));
            p.findField().setText("");
            assertEquals(434, p.grid().count());
            p.grid().select(10_000);
            assertEquals(coverage.stream().max().orElseThrow(), p.grid().selectedCodePoint());
            assertTrue(p.grid().selectCodePoint('A'));
            p.copyCharacter();
        });
    }

    @Test
    void waterfallGrowsWithItsText() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Path file = TestFonts.caladea(dir);
        Font font = new FontLoader().font(file, 0).orElseThrow();
        OnEdt.run(() -> {
            Waterfall w = new Waterfall();
            w.setSize(600, 100);
            w.show(font, "Short", "");
            int one = w.getPreferredSize().height;
            w.show(font, "Short\nTwo lines", "");
            assertTrue(w.getPreferredSize().height > one);
            w.show(null, "", "cannot");
            assertTrue(w.getPreferredSize().height < one);
            assertEquals("", w.text());
        });
    }
}
