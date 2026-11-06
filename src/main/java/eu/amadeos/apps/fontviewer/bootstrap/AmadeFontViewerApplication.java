package eu.amadeos.apps.fontviewer.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Starts the Font Viewer: without arguments the main window, with font files a window for each file (that is
 * what a double-click in Files does), with {@code --family=NAME} the main window with that family chosen.
 */
public final class AmadeFontViewerApplication {
    public static final AppInfo APP = new AppInfo("amade-fontviewer", "Font Viewer", "fontviewer");
    public static final String FAMILY = "--family=";

    private AmadeFontViewerApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeFontViewerApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-fontviewer " + version());
            return;
        }
        List<Path> files = files(args);
        Optional<String> family = family(args);
        AmadeApplication.launch(APP, injector -> {
            Windows windows = injector.getInstance(Windows.class);
            if (files.isEmpty()) windows.showMain(family);
            else files.forEach(windows::openFile);
        }, new FontViewerModule());
    }
    // --8<-- [end:main]

    /** The arguments that are files; {@code file://} URIs (some file managers pass them) become paths. */
    static List<Path> files(String[] args) {
        return Arrays.stream(args).filter(a -> !a.startsWith("--")).map(a -> {
            if (a.startsWith("file://")) {
                try {
                    return Path.of(java.net.URI.create(a));
                } catch (IllegalArgumentException e) {
                    return Path.of(a.substring(7));
                }
            }
            return Path.of(a);
        }).toList();
    }

    static Optional<String> family(String[] args) {
        return Arrays.stream(args).filter(a -> a.startsWith(FAMILY)).map(a -> a.substring(FAMILY.length()))
                .filter(s -> !s.isBlank()).findFirst();
    }
}
