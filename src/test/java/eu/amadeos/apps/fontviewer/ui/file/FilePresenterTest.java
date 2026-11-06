package eu.amadeos.apps.fontviewer.ui.file;

import eu.amadeos.apps.fontviewer.FakeRunner;
import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.service.FontCatalog;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontInstaller;
import eu.amadeos.apps.fontviewer.ui.main.Fixture;
import eu.amadeos.commons.config.XdgDirectories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePresenterTest {
    @TempDir
    Path dir;

    private Fixture f;
    private final List<FileModel> shown = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> viewer = new ArrayList<>();
    private boolean confirm = true;
    private FilePresenter presenter;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
        FileView view = new FileView() {
            @Override
            public void showFile(FileModel model) {
                shown.add(model);
            }

            @Override
            public void showBusy(String text) {
            }

            @Override
            public void showError(String message) {
                errors.add(message);
            }

            @Override
            public boolean confirm(String message, String yesLabel) {
                return confirm;
            }

            @Override
            public void close() {
            }
        };
        XdgDirectories xdg = new XdgDirectories(Map.of(), dir.resolve("home").toString());
        presenter = new FilePresenter(view, f.msg, new FilePresenter.Services(new FontCatalog(f.runner, f.places),
                f.loader, new FontInspector(f.loader), new FontInstaller(f.runner, f.places, List.of("pkexec", "helper")),
                f.places, xdg, viewer::add, f.direct, f.direct));
    }

    @Test
    void aDownloadedFontThatIsNotInstalled() throws Exception {
        f.fcList = "";
        Path file = TestFonts.caladea(dir.resolve("home/Downloads"));
        presenter.open(file);
        FileModel m = shown.getLast();
        assertEquals("Caladea", m.title());
        assertEquals(FileModel.State.NOT_INSTALLED, m.state());
        assertEquals("Downloads/Caladea-Regular.ttf · Not installed", m.stateText());
        assertTrue(m.font().isPresent());
        assertEquals("Regular", m.details().getFirst().value());
        presenter.install(false);
        assertTrue(Files.exists(f.places.userFonts().resolve("Caladea-Regular.ttf")));
    }

    @Test
    void aFontThatIsInstalledAlready() throws Exception {
        Path file = TestFonts.caladea(dir.resolve("home/Downloads"));
        presenter.open(file);
        assertEquals(FileModel.State.INSTALLED, shown.getLast().state());
        assertEquals("Downloads/Caladea-Regular.ttf · Installed by you", shown.getLast().stateText());
        presenter.showInViewer();
        assertEquals(List.of("Caladea"), viewer);
        presenter.open(f.caladea);
        assertEquals(FileModel.State.INSTALLED, shown.getLast().state());
    }

    @Test
    void installForAllAndFailures() throws Exception {
        f.fcList = "";
        Path file = TestFonts.caladea(dir.resolve("home/Downloads"), "Cal.ttf");
        presenter.open(file);
        presenter.install(true);
        assertTrue(f.runner.lines().contains("pkexec helper install Cal.ttf"));
        f.runner.answer("pkexec", c -> new FakeRunner.Recording("@@error disk full\n", 1));
        presenter.install(true);
        assertEquals(List.of("The font could not be installed or removed: disk full"), errors);
    }

    @Test
    void notAFont() throws Exception {
        Path text = dir.resolve("home/x.ttf");
        Files.createDirectories(text.getParent());
        Files.writeString(text, "hello");
        presenter.open(text);
        assertEquals(FileModel.State.NOT_A_FONT, shown.getLast().state());
        presenter.install(false);
        assertEquals(1, shown.size());
    }

    @Test
    void replacingNeedsAYes() throws Exception {
        f.fcList = "";
        Path file = TestFonts.caladea(dir.resolve("home/Downloads"));
        presenter.open(file);
        confirm = false;
        int before = f.runner.commands.size();
        presenter.install(false);
        assertEquals(before, f.runner.commands.size(), "the user's copy stays");
    }
}
