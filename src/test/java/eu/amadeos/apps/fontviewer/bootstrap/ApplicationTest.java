package eu.amadeos.apps.fontviewer.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTest {
    @Test
    void arguments() {
        assertEquals(List.of(Path.of("/tmp/a b.ttf"), Path.of("x.otf")),
                AmadeFontViewerApplication.files(new String[]{"file:///tmp/a%20b.ttf", "--family=Inter", "x.otf"}));
        assertEquals(Optional.of("Inter"), AmadeFontViewerApplication.family(new String[]{"--family=Inter"}));
        assertEquals(Optional.empty(), AmadeFontViewerApplication.family(new String[]{"--family="}));
        assertEquals(List.of(), AmadeFontViewerApplication.files(new String[]{}));
    }

    @Test
    void versionWithoutAJar() {
        assertEquals("dev", AmadeFontViewerApplication.version());
    }

    @Test
    void appInfo() {
        assertEquals("amade-fontviewer", AmadeFontViewerApplication.APP.id());
        assertEquals("fontviewer", AmadeFontViewerApplication.APP.configName());
    }
}
