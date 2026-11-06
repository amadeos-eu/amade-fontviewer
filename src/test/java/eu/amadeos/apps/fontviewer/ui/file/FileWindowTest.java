package eu.amadeos.apps.fontviewer.ui.file;

import eu.amadeos.apps.fontviewer.OnEdt;
import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.service.FontCatalog;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontInstaller;
import eu.amadeos.apps.fontviewer.ui.main.Fixture;
import eu.amadeos.commons.config.XdgDirectories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class FileWindowTest {
    @TempDir
    Path dir;

    private FileWindow window;

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
    }

    @Test
    void showsAFileAndItsButtons() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Fixture f = new Fixture(dir);
        f.fcList = "";
        Path file = TestFonts.caladea(dir.resolve("home/Downloads"), "Cal.ttf");
        List<String> viewer = new ArrayList<>();
        OnEdt.run(() -> {
            window = new FileWindow(f.msg);
            FilePresenter p = new FilePresenter(window, f.msg, new FilePresenter.Services(
                    new FontCatalog(f.runner, f.places), f.loader, new FontInspector(f.loader),
                    new FontInstaller(f.runner, f.places, List.of("pkexec", "helper")), f.places,
                    new XdgDirectories(Map.of(), dir.resolve("home").toString()), viewer::add, f.direct, f.direct));
            window.bind(p);
            window.setVisible(true);
            p.open(file);
            assertEquals("Caladea", window.titleText());
            assertEquals("Cal.ttf - Font Viewer", window.getTitle());
            assertEquals("Downloads/Cal.ttf · Not installed", window.stateText());
            assertTrue(window.installButton().isVisible());
            assertTrue(window.installAllButton().isVisible());
            assertFalse(window.showButton().isVisible());
            assertEquals("Style", window.detailsPanel().details().getFirst().label());
            f.fcList = Fixture.line(f.places.userFonts().resolve("Cal.ttf").toString(), "Caladea", "Regular", "80",
                    "0", "");
            window.installButton().doClick();
            assertEquals(eu.amadeos.apps.fontviewer.ui.file.FileModel.State.INSTALLED,
                    window.model().orElseThrow().state());
            assertTrue(window.showButton().isVisible());
            assertFalse(window.installButton().isVisible());
            window.showButton().doClick();
            assertEquals(List.of("Caladea"), viewer);
        });
    }
}
