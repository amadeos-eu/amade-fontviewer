package eu.amadeos.apps.fontviewer.ui.main;

import java.io.IOException;
import java.nio.file.Path;

/** Starts other programs: Files for a font's folder, Software for more fonts, the browser for the handbook. */
public interface Opener {
    void showInFolder(Path file) throws IOException;

    void getMoreFonts() throws IOException;

    void handbook() throws IOException;
}
