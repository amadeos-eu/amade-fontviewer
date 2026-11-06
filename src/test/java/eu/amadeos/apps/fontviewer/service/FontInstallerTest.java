package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.FakeRunner;
import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontScope;
import eu.amadeos.commons.process.FinishedProcess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontInstallerTest {
    @TempDir
    Path dir;

    private FontPlaces places;
    private FakeRunner runner;
    private FontInstaller installer;
    private Path font;

    @BeforeEach
    void setUp() throws Exception {
        places = new FontPlaces(dir.resolve("home/.local/share/fonts"), dir.resolve("home/.fonts"),
                dir.resolve("usr/local/share/fonts/amadeos"));
        runner = new FakeRunner().prints("fc-cache", "").prints("gio", "")
                .answer("pkexec", c -> new FakeRunner.Recording("@@done\n", 0));
        installer = new FontInstaller(runner, places, List.of("pkexec", FontInstaller.HELPER));
        font = TestFonts.caladea(dir.resolve("Downloads"), "Caladea Regular (1).ttf");
    }

    @Test
    void installsForMe() throws Exception {
        List<Path> installed = installer.installForMe(List.of(font));
        Path target = places.userFonts().resolve("Caladea Regular _1_.ttf");
        assertEquals(List.of(target), installed);
        assertArrayEquals(Files.readAllBytes(font), Files.readAllBytes(target));
        assertTrue(runner.lines().contains("fc-cache -f " + places.userFonts()));
        try (var files = Files.list(places.userFonts())) {
            assertEquals(1, files.count(), "no .part file is left");
        }
        assertEquals(List.of(font), installer.existing(List.of(font), false));
        assertEquals(List.of(), installer.existing(List.of(font), true));
    }

    @Test
    void installingAFontThatIsThereAlreadyKeepsIt() throws Exception {
        Path inPlace = TestFonts.caladea(places.userFonts());
        assertEquals(List.of(inPlace), installer.installForMe(List.of(inPlace)));
        assertTrue(Files.exists(inPlace));
    }

    @Test
    void refusesFilesThatAreNoFonts() throws Exception {
        Path fake = dir.resolve("Downloads/passwords.ttf");
        Files.writeString(fake, "secret");
        InstallException e = assertThrows(InstallException.class, () -> installer.installForMe(List.of(fake)));
        assertEquals(InstallException.Reason.NOT_A_FONT, e.reason());
        Path wrongEnding = dir.resolve("Downloads/font.txt");
        Files.copy(font, wrongEnding);
        assertThrows(InstallException.class, () -> installer.installForAll(List.of(wrongEnding)));
        assertTrue(runner.commands.stream().noneMatch(c -> c.getFirst().equals("pkexec")));
    }

    @Test
    void installsForAllThroughTheHelperWithTheFontOnStandardInput() throws Exception {
        List<Path> installed = installer.installForAll(List.of(font));
        assertEquals(List.of(places.allUsersFonts().resolve("Caladea Regular _1_.ttf")), installed);
        assertEquals(List.of("pkexec", FontInstaller.HELPER, "install", "Caladea Regular _1_.ttf"),
                runner.commands.getFirst());
        assertArrayEquals(Files.readAllBytes(font), runner.inputs.getFirst());
    }

    @Test
    void helperFailures() {
        runner.answer("pkexec", c -> new FakeRunner.Recording("", 126));
        assertEquals(InstallException.Reason.DISMISSED, assertThrows(InstallException.class,
                () -> installer.installForAll(List.of(font))).reason());
        runner.answer("pkexec", c -> new FakeRunner.Recording("", 127));
        assertEquals(InstallException.Reason.NOT_AUTHORIZED, assertThrows(InstallException.class,
                () -> installer.installForAll(List.of(font))).reason());
        runner.answer("pkexec", c -> new FakeRunner.Recording("@@error format not a font\n", 1));
        assertEquals(InstallException.Reason.NOT_A_FONT, assertThrows(InstallException.class,
                () -> installer.installForAll(List.of(font))).reason());
        runner.answer("pkexec", c -> new FakeRunner.Recording("@@error disk full\n", 1));
        InstallException e = assertThrows(InstallException.class, () -> installer.installForAll(List.of(font)));
        assertEquals(InstallException.Reason.FAILED, e.reason());
        assertEquals("disk full", e.getMessage());
        FontInstaller noPkexec = new FontInstaller(new FakeRunner(), places, List.of("pkexec", "x"));
        assertEquals(InstallException.Reason.NOT_AUTHORIZED, assertThrows(InstallException.class,
                () -> noPkexec.installForAll(List.of(font))).reason());
    }

    @Test
    void removesOwnFontsToTheWastebasketAndLeavesPackageFonts() throws Exception {
        Path own = TestFonts.caladea(places.userFonts());
        Path forAll = places.allUsersFonts().resolve("Caladea-Bold.ttf");
        FontFace mine = face(own, FontScope.USER);
        FontFace all = face(forAll, FontScope.ALL_USERS);
        FontFace system = face(Path.of("/usr/share/fonts/x.ttf"), FontScope.SYSTEM);
        List<Path> removed = installer.remove(List.of(mine, all, system));
        assertEquals(List.of(own, forAll), removed);
        assertTrue(runner.lines().contains("gio trash " + own));
        assertTrue(runner.lines().contains("pkexec " + FontInstaller.HELPER + " remove Caladea-Bold.ttf"));
        assertTrue(runner.lines().contains("fc-cache -f " + places.userFonts()));
        assertFalse(runner.lines().stream().anyMatch(l -> l.contains("/usr/share/fonts/x.ttf")));
    }

    @Test
    void deletesWhenThereIsNoWastebasket() throws Exception {
        Path own = TestFonts.caladea(places.userFonts());
        runner.answer("gio", c -> FinishedProcess.failing(2, "no trash"));
        installer.remove(List.of(face(own, FontScope.USER)));
        assertFalse(Files.exists(own));
    }

    @Test
    void helperCommand() {
        assertEquals(List.of("pkexec", FontInstaller.HELPER), FontInstaller.helperCommand(null, "anna"));
        assertEquals(List.of(FontInstaller.HELPER), FontInstaller.helperCommand("", "root"));
        assertEquals(List.of("sh", "/tmp/h"), FontInstaller.helperCommand(" sh /tmp/h ", "anna"));
    }

    private static FontFace face(Path file, FontScope scope) {
        return new FontFace(file, 0, "Caladea", "Regular", 80, 0, false, "TrueType", Set.of(), scope);
    }
}
