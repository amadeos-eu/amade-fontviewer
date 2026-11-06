package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.FakeRunner;
import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.SampleText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path dir;

    private Fixture f;
    private FakeView view;
    private MainPresenter p;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
        view = new FakeView();
        p = f.presenter(view);
    }

    private List<String> names() {
        return view.families.stream().map(FontFamily::name).toList();
    }

    @Test
    void startListsFamiliesAndChoosesTheFirst() {
        p.start(Optional.empty());
        assertEquals(List.of("Caladea", "DejaVu Sans Mono", "Liberation Serif"), names());
        assertEquals("Caladea", view.selected.orElseThrow().name());
        assertEquals("Caladea", view.family.family().name());
        assertTrue(view.family.font().isPresent());
        assertTrue(view.family.removable());
        assertEquals("Serif · 1 style · Installed by you", view.family.subtitle());
        assertEquals("3 font families, 1 of them installed by you", view.status);
        assertEquals(0, view.page);
        assertEquals(SampleText.SENTENCE, view.sample);
        assertEquals("The quick brown fox jumps over the lazy dog.", view.sampleText);
        assertEquals(434, view.coverage.size());
        assertEquals("1.001", view.detail("Version"));
        assertEquals("SIL Open Font License 1.1 (free to use, also in commercial work)", view.detail("Licence"));
        assertEquals("434 in 16 Unicode blocks", view.detail("Characters"));
        assertTrue(view.detail("Languages").startsWith("German, English, French"));
        assertEquals("OpenType with TrueType outlines, kerning", view.detail("Format"));
        assertEquals("Installed by you", view.detail("Installed"));
        assertEquals(f.caladea.toString(), view.detail("Files"));
        assertTrue(view.refreshes > 0);
        assertEquals(Optional.of("Caladea"), f.prefs.lastFamily());
    }

    @Test
    void startChoosesTheFamilyShownLast() {
        f.prefs.setLastFamily("Liberation Serif");
        p.start(Optional.empty());
        assertEquals("Liberation Serif", view.family.family().name());
        assertFalse(view.family.removable());
        assertEquals("This font came with a program package. You remove it in Software.", view.family.removeHint());
        assertEquals("Serif · 2 styles · System font, comes with the package fonts-liberation", view.family.subtitle());
        assertTrue(view.family.font().isEmpty(), "the file does not exist here");
        p.start(Optional.of("dejavu sans mono"));
        assertEquals("DejaVu Sans Mono", view.family.family().name());
        assertEquals("Monospace · 1 style · System font", view.family.subtitle());
    }

    @Test
    void filterAndSearch() {
        p.start(Optional.empty());
        p.setFilter(Filter.MONOSPACE);
        assertEquals(List.of("DejaVu Sans Mono"), names());
        assertEquals("DejaVu Sans Mono", view.family.family().name());
        assertEquals("1 of 3 font families", view.status);
        p.setFilter(Filter.MINE);
        assertEquals(List.of("Caladea"), names());
        p.setFilter(Filter.ALL);
        p.setQuery("lib");
        assertEquals(List.of("Liberation Serif"), names());
        p.setQuery("nothing like this");
        assertEquals(List.of(), names());
        assertTrue(view.noFamily);
        assertTrue(p.family().isEmpty());
        p.setQuery("");
        assertEquals(3, names().size());
    }

    @Test
    void stylesAndSamples() {
        f.prefs.setLastFamily("Liberation Serif");
        p.start(Optional.empty());
        p.selectStyle("Bold");
        assertEquals("Bold", view.family.face().style());
        assertEquals("Bold", p.face().orElseThrow().style());
        p.setSample(SampleText.ALPHABET);
        assertEquals(SampleText.ALPHABET_TEXT, view.sampleText);
        p.setSample(SampleText.NUMBERS);
        assertEquals(SampleText.NUMBERS_TEXT, view.sampleText);
        p.setSample(SampleText.PARAGRAPH);
        assertTrue(view.sampleText.startsWith("Typography"));
        p.setSample(SampleText.OWN);
        assertEquals("The quick brown fox jumps over the lazy dog.", view.sampleText, "blank own text = sentence");
        p.setSample(SampleText.SENTENCE);
        p.setOwnText("Grüße");
        assertEquals(SampleText.OWN, view.sample);
        assertEquals("Grüße", view.sampleText);
        assertEquals(SampleText.OWN, f.prefs.sample());
        assertEquals("Grüße", f.prefs.ownText());
        p.setPage(1);
        assertEquals(1, view.page);
        assertEquals(1, f.prefs.page());
    }

    @Test
    void compare() {
        p.start(Optional.empty());
        p.openCompare();
        assertEquals(List.of("Caladea"), view.compared, "the chosen family when nothing was compared yet");
        p.addToCompare();
        p.select(p.find("Liberation Serif").orElseThrow());
        p.addToCompare();
        assertEquals(List.of("Liberation Serif", "Caladea"), view.compared);
        f.prefs.setCompared(List.of("Gone", "Caladea"));
        p.openCompare();
        assertEquals(List.of("Caladea"), view.compared);
    }

    @Test
    void installForMeCopiesAndChoosesTheNewFamily() throws Exception {
        Path download = TestFonts.caladea(dir.resolve("Downloads"), "Other.ttf");
        p.start(Optional.of("Liberation Serif"));
        f.fcList += "\n" + Fixture.line(f.places.userFonts().resolve("Other.ttf").toString(), "Caladea", "Italic",
                "80", "100", "");
        p.install(List.of(download, dir.resolve("notes.txt")));
        assertTrue(Files.exists(f.places.userFonts().resolve("Other.ttf")));
        assertEquals(List.of("The font is installed. Programs that are open now see it after you start them again."),
                view.infos);
        assertEquals("Caladea", view.family.family().name());
        assertTrue(f.runner.lines().contains("fc-cache -f " + f.places.userFonts()));
    }

    @Test
    void installAsksBeforeReplacing() throws Exception {
        Path download = TestFonts.caladea(dir.resolve("Downloads"));
        p.start(Optional.empty());
        view.confirmAnswer = false;
        p.install(List.of(download));
        assertEquals(1, view.confirmations.size());
        assertTrue(view.confirmations.getFirst().contains("Caladea-Regular.ttf"));
        assertTrue(view.infos.isEmpty());
    }

    @Test
    void installForAllUsesTheHelper() throws Exception {
        Path download = TestFonts.caladea(dir.resolve("Downloads"), "New.ttf");
        p.start(Optional.empty());
        view.target = Optional.of(InstallTarget.FOR_ALL);
        p.install(List.of(download));
        assertTrue(f.runner.lines().contains("pkexec helper install New.ttf"));
        assertTrue(view.infos.getFirst().startsWith("The font is installed for all users"));
        f.runner.answer("pkexec", c -> new FakeRunner.Recording("", 127));
        p.install(List.of(download));
        assertEquals(List.of("Fonts for all users need an administrator's password, and this was refused."),
                view.errors);
        f.runner.answer("pkexec", c -> new FakeRunner.Recording("", 126));
        p.install(List.of(download));
        assertEquals("Cancelled.", view.status);
    }

    @Test
    void installCancelledOrNothingToInstall() throws Exception {
        p.start(Optional.empty());
        p.install(List.of(dir.resolve("a.txt")));
        assertTrue(view.errors.getFirst().startsWith("None of the files is a font"));
        view.target = Optional.empty();
        p.install(List.of(TestFonts.caladea(dir.resolve("D"))));
        assertTrue(view.infos.isEmpty());
        view.chosen = List.of();
        p.installFonts();
        assertTrue(view.infos.isEmpty());
        Path bad = dir.resolve("bad.ttf");
        Files.writeString(bad, "not a font");
        view.target = Optional.of(InstallTarget.FOR_ME);
        view.chosen = List.of(bad);
        p.installFonts();
        assertTrue(view.errors.getLast().startsWith("This is not a font file"));
    }

    @Test
    void removeOwnFamily() {
        p.start(Optional.empty());
        view.confirmAnswer = false;
        p.removeFamily();
        assertTrue(Files.exists(f.caladea));
        assertTrue(view.confirmations.getFirst().startsWith("Remove the font Caladea (one file)?"));
        view.confirmAnswer = true;
        f.fcList = f.fcList.lines().filter(l -> !l.contains("Caladea")).reduce((a, b) -> a + "\n" + b).orElse("");
        p.removeFamily();
        assertTrue(f.runner.lines().contains("gio trash " + f.caladea));
        assertEquals(List.of("Caladea was removed."), view.infos);
        assertEquals(List.of("DejaVu Sans Mono", "Liberation Serif"), names());
    }

    @Test
    void packageFontsAreNotRemoved() {
        p.start(Optional.of("Liberation Serif"));
        p.removeFamily();
        assertTrue(view.confirmations.isEmpty());
        assertEquals(List.of("This font came with a program package. You remove it in Software."), view.infos);
    }

    @Test
    void listFailure() {
        f.runner.answer("fc-list", c -> eu.amadeos.commons.process.FinishedProcess.failing(1, "no fontconfig"));
        p.start(Optional.empty());
        assertTrue(view.errors.getFirst().startsWith("The font list could not be read"));
    }

    @Test
    void openersAndExit() {
        p.start(Optional.empty());
        p.showInFolder();
        p.getMoreFonts();
        p.handbook();
        assertEquals(List.of("folder " + f.caladea, "software", "handbook"), f.opened);
        view.chosen = List.of(dir.resolve("x.ttf"));
        p.openFontFile();
        assertEquals(List.of(dir.resolve("x.ttf")), f.openedFiles);
        p.about();
        assertTrue(view.about);
        p.reveal("Liberation Serif");
        assertEquals("Liberation Serif", view.family.family().name());
        p.reload();
        assertEquals("Liberation Serif", view.family.family().name());
        p.exit();
        assertTrue(view.closed);
        assertEquals(1, f.exits);
        assertNotNull(p.families());
    }
}
