package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.FakeRunner;
import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontScope;
import eu.amadeos.apps.fontviewer.domain.Outlines;
import eu.amadeos.commons.desktop.DesktopLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallServicesTest {
    @TempDir
    Path dir;

    @Test
    void loaderReadsAndKeepsFonts() throws Exception {
        Path file = TestFonts.caladea(dir);
        FontLoader loader = new FontLoader();
        FontFace face = new FontFace(file, 0, "Caladea", "Regular", 80, 0, false, "TrueType", Set.of(), FontScope.USER);
        assertTrue(loader.cached(face).isEmpty());
        Font font = loader.font(face).orElseThrow();
        assertEquals("Caladea", font.getFamily());
        assertSame(font, loader.cached(face).orElseThrow());
        loader.forget(file);
        assertTrue(loader.cached(face).isEmpty());
        assertTrue(loader.font(file, 3).isEmpty());
        FontFace bitmap = new FontFace(file, 0, "X", "R", 80, 0, false, "PCF", Set.of(), FontScope.SYSTEM);
        assertTrue(loader.font(bitmap).isEmpty());
        Path broken = dir.resolve("broken.ttf");
        Files.writeString(broken, "no font");
        assertTrue(loader.font(broken, 0).isEmpty());
    }

    @Test
    void inspectorUsesTablesAndKeepsResults() throws Exception {
        Path file = TestFonts.caladea(dir);
        FontInspector inspector = new FontInspector(new FontLoader());
        FontInspector.Inspection a = inspector.inspect(file, 0);
        assertEquals(434, a.coverage().size());
        assertEquals(Outlines.TRUETYPE, a.details().outlines());
        assertSame(a, inspector.inspect(file, 0));
        inspector.forget(file);
        Path broken = dir.resolve("broken.ttf");
        Files.writeString(broken, "no font");
        FontInspector.Inspection none = inspector.inspect(broken, 0);
        assertTrue(none.coverage().isEmpty());
    }

    @Test
    void serifShapeLooksAtTheLetters() throws Exception {
        assertTrue(SerifShape.hasSerifs(TestFonts.caladea(dir), 0), "Caladea stands on serifs");
        Font sans = new Font("SansSerif", Font.PLAIN, 12);
        if (sans.getFontName().toLowerCase(java.util.Locale.ROOT).matches(".*(dejavu sans|liberation sans|inter).*")) {
            assertFalse(SerifShape.hasSerifs(sans), sans.getFontName());
        }
        Path broken = dir.resolve("broken.ttf");
        Files.writeString(broken, "no font");
        assertFalse(SerifShape.hasSerifs(broken, 0));
        assertFalse(SerifShape.hasSerifs(dir.resolve("missing.ttf"), 0));
        assertFalse(SerifShape.hasSerifs(TestFonts.caladea(dir), 5));
    }

    @Test
    void styleFromJavaNames() {
        assertEquals("Regular", FontInspector.styleOf(new Font("Dialog", Font.PLAIN, 1)));
    }

    @Test
    void packageOwner() {
        assertEquals(Optional.of("fonts-inter"),
                PackageOwner.parse("fonts-inter: /usr/share/fonts/opentype/inter/Inter-Regular.otf\n"));
        assertEquals(Optional.of("fonts-x"), PackageOwner.parse("fonts-x:amd64: /usr/share/fonts/x.ttf"));
        assertEquals(Optional.empty(), PackageOwner.parse("diversion by a from: /x"));
        assertEquals(Optional.empty(), PackageOwner.parse(""));
        FakeRunner runner = new FakeRunner().prints("dpkg", "fonts-inter: /f.otf\n");
        PackageOwner owner = new PackageOwner(runner);
        assertEquals(Optional.of("fonts-inter"), owner.packageOf(Path.of("/f.otf")));
        owner.packageOf(Path.of("/f.otf"));
        assertEquals(1, runner.commands.size(), "asked once");
        assertEquals(Optional.empty(), new PackageOwner(new FakeRunner()).packageOf(Path.of("/f.otf")));
    }

    @Test
    void opener() throws Exception {
        List<String> opened = new ArrayList<>();
        DesktopLauncher launcher = new DesktopLauncher() {
            @Override
            public void open(Path file) {
                opened.add("open " + file);
            }

            @Override
            public void browse(String uri) {
                opened.add("browse " + uri);
            }
        };
        FakeRunner runner = new FakeRunner().prints("amadeos-software", "");
        DesktopOpener opener = new DesktopOpener(launcher, runner);
        opener.showInFolder(Path.of("/usr/share/fonts/x/a.ttf"));
        opener.handbook();
        opener.getMoreFonts();
        assertEquals(List.of("open /usr/share/fonts/x", "browse " + DesktopOpener.HANDBOOK), opened);
        assertEquals(List.of("amadeos-software --search=fonts-"), runner.lines());
        DesktopOpener missing = new DesktopOpener(launcher, new FakeRunner());
        try {
            missing.getMoreFonts();
        } catch (IOException e) {
            return;
        }
        throw new AssertionError("expected a failure");
    }
}
