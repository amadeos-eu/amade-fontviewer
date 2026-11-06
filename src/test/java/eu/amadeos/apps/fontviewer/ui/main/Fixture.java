package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.Direct;
import eu.amadeos.apps.fontviewer.FakeRunner;
import eu.amadeos.apps.fontviewer.TestFonts;
import eu.amadeos.apps.fontviewer.bootstrap.FontViewerModule;
import eu.amadeos.apps.fontviewer.repository.FontViewerPreferences;
import eu.amadeos.apps.fontviewer.service.FontCatalog;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontInstaller;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.apps.fontviewer.service.FontPlaces;
import eu.amadeos.apps.fontviewer.service.PackageOwner;
import eu.amadeos.commons.i18n.Messages;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A presenter on test data: Caladea installed by the user (a real file), a package family "Liberation Serif"
 * and a monospace family whose files do not exist, and a fake fontconfig, dpkg, gio and pkexec.
 */
public final class Fixture {
    public final Path dir;
    public final Messages msg = Messages.load(FontViewerModule.class, FontViewerModule.MESSAGES, Locale.ENGLISH);
    public final FontPlaces places;
    public final FakeRunner runner = new FakeRunner();
    public final FontLoader loader = new FontLoader();
    public final FontViewerPreferences prefs;
    public final List<String> opened = new ArrayList<>();
    public final List<Path> openedFiles = new ArrayList<>();
    public final Path caladea;
    public final Direct direct = new Direct();
    public int exits;
    public String fcList;
    public MainPresenter presenter;

    public Fixture(Path dir) throws IOException {
        this.dir = dir;
        places = new FontPlaces(dir.resolve("home/.local/share/fonts"), dir.resolve("home/.fonts"),
                dir.resolve("usr/local/share/fonts/amadeos"));
        caladea = TestFonts.caladea(places.userFonts());
        prefs = new FontViewerPreferences(dir.resolve("prefs.properties"));
        fcList = String.join("\n",
                line(caladea.toString(), "Caladea", "Regular", "80", "0", ""),
                line("/no/such/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf", "Liberation Serif", "Regular",
                        "80", "0", ""),
                line("/no/such/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf", "Liberation Serif", "Bold",
                        "200", "0", ""),
                line("/no/such/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", "DejaVu Sans Mono", "Book", "80", "0",
                        "100"));
        runner.answer("fc-list", c -> eu.amadeos.commons.process.FinishedProcess.printing(fcList))
                .prints("fc-cache", "").prints("gio", "")
                .answer("dpkg", c -> c.getLast().contains("liberation")
                        ? eu.amadeos.commons.process.FinishedProcess.printing("fonts-liberation: " + c.getLast() + "\n")
                        : eu.amadeos.commons.process.FinishedProcess.failing(1, "not found"))
                .answer("pkexec", c -> new FakeRunner.Recording("@@done\n", 0));
    }

    public static String line(String file, String family, String style, String weight, String slant, String spacing) {
        return String.join("\t", file, "0", family, style, weight, slant, spacing, "TrueType", "False", "de|en|fr");
    }

    public MainPresenter presenter(MainView view) {
        Opener opener = new Opener() {
            @Override
            public void showInFolder(Path file) {
                opened.add("folder " + file);
            }

            @Override
            public void getMoreFonts() {
                opened.add("software");
            }

            @Override
            public void handbook() {
                opened.add("handbook");
            }
        };
        presenter = new MainPresenter(view, msg, null, new MainPresenter.Services(new FontCatalog(runner, places),
                loader, new FontInspector(loader), new FontInstaller(runner, places, List.of("pkexec", "helper")),
                new PackageOwner(runner), places, prefs, opener, openedFiles::add, direct, direct, () -> exits++));
        return presenter;
    }
}
