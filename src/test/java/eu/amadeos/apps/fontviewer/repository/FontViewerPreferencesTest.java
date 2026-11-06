package eu.amadeos.apps.fontviewer.repository;

import eu.amadeos.apps.fontviewer.domain.SampleText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FontViewerPreferencesTest {
    @TempDir
    Path dir;

    @Test
    void defaults() {
        FontViewerPreferences p = new FontViewerPreferences(dir.resolve("p.properties"));
        assertEquals(SampleText.SENTENCE, p.sample());
        assertEquals("", p.ownText());
        assertEquals(Optional.empty(), p.lastFamily());
        assertEquals(0, p.page());
        assertEquals(List.of(), p.compared());
        assertEquals(28, p.compareSize());
    }

    @Test
    void keepsWhatIsSet() {
        Path file = dir.resolve("p.properties");
        FontViewerPreferences p = new FontViewerPreferences(file);
        p.setSample(SampleText.OWN);
        p.setOwnText("Grüße aus Köln");
        p.setLastFamily("Caladea");
        p.setPage(2);
        p.setCompared(List.of("A", "B", "C", "D", "E", "F", "G"));
        p.setCompareSize(48);
        FontViewerPreferences again = new FontViewerPreferences(file);
        assertEquals(SampleText.OWN, again.sample());
        assertEquals("Grüße aus Köln", again.ownText());
        assertEquals(Optional.of("Caladea"), again.lastFamily());
        assertEquals(2, again.page());
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), again.compared());
        assertEquals(48, again.compareSize());
        p.setOwnText("x".repeat(3000));
        assertEquals(2000, new FontViewerPreferences(file).ownText().length());
    }

    @Test
    void brokenValuesFallBack() throws Exception {
        Path file = dir.resolve("p.properties");
        Files.writeString(file, "sample=NONSENSE\npage=99\ncompare.size=abc\ncompare= | A | A |\n");
        FontViewerPreferences p = new FontViewerPreferences(file);
        assertEquals(SampleText.SENTENCE, p.sample());
        assertEquals(2, p.page());
        assertEquals(28, p.compareSize());
        assertEquals(List.of("A"), p.compared());
    }
}
