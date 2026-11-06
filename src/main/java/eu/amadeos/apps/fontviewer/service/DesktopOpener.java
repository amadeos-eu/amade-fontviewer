package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.ui.main.Opener;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.process.CommandRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Opens folders and the handbook through the desktop, and Software with the font packages. */
public final class DesktopOpener implements Opener {
    /** The handbook section about fonts and the Font Viewer. */
    public static final String HANDBOOK = "https://docs.amadeos.eu/amadeos/35-font-window-startup/#font-viewer";
    /** Software shows the packages whose name or summary contains this. */
    public static final List<String> SOFTWARE = List.of("amadeos-software", "--search=fonts-");

    private final DesktopLauncher launcher;
    private final CommandRunner runner;

    public DesktopOpener(DesktopLauncher launcher, CommandRunner runner) {
        this.launcher = launcher;
        this.runner = runner;
    }

    @Override
    public void showInFolder(Path file) throws IOException {
        Path folder = file.getParent();
        if (folder != null) launcher.open(folder);
    }

    @Override
    public void getMoreFonts() throws IOException {
        runner.start(SOFTWARE);
    }

    @Override
    public void handbook() throws IOException {
        launcher.browse(HANDBOOK);
    }
}
