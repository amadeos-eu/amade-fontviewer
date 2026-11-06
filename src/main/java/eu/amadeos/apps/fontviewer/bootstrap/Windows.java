package eu.amadeos.apps.fontviewer.bootstrap;

import eu.amadeos.apps.fontviewer.repository.FontViewerPreferences;
import eu.amadeos.apps.fontviewer.service.FontCatalog;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontInstaller;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.apps.fontviewer.service.FontPlaces;
import eu.amadeos.apps.fontviewer.service.PackageOwner;
import eu.amadeos.apps.fontviewer.ui.file.FilePresenter;
import eu.amadeos.apps.fontviewer.ui.file.FileWindow;
import eu.amadeos.apps.fontviewer.ui.main.MainPresenter;
import eu.amadeos.apps.fontviewer.ui.main.MainWindow;
import eu.amadeos.apps.fontviewer.ui.main.Opener;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * The windows of the program: one main window, made when it is first needed, and a window per opened font file.
 * The program ends when the last window is closed.
 */
public final class Windows {
    /** Everything the windows need. */
    public record Parts(Messages msg, LocaleSettings locales, FontCatalog catalog, FontLoader loader,
                        FontInspector inspector, FontInstaller installer, PackageOwner owner, FontPlaces places,
                        FontViewerPreferences prefs, Opener opener, XdgDirectories xdg, ExecutorService background,
                        Executor ui, Runnable exit) {
    }

    private final Parts p;
    private MainWindow main;
    private MainPresenter presenter;

    public Windows(Parts parts) {
        this.p = parts;
    }

    /** Shows the main window, with this family chosen when given. */
    public void showMain(Optional<String> family) {
        EdtExecutor.requireEdt();
        if (main == null || !main.isDisplayable()) {
            main = new MainWindow(p.msg(), p.locales(), p.loader(), p.prefs());
            presenter = new MainPresenter(main, p.msg(), p.locales(), new MainPresenter.Services(p.catalog(),
                    p.loader(), p.inspector(), p.installer(), p.owner(), p.places(), p.prefs(), p.opener(),
                    this::openFile, p.background(), p.ui(), this::closed));
            main.bind(presenter);
            main.setVisible(true);
            presenter.start(family);
        } else {
            family.ifPresent(presenter::reveal);
            main.toFront();
        }
    }

    /** Opens the window of one font file. */
    public void openFile(Path file) {
        EdtExecutor.requireEdt();
        FileWindow window = new FileWindow(p.msg());
        FilePresenter filePresenter = new FilePresenter(window, p.msg(), new FilePresenter.Services(p.catalog(),
                p.loader(), p.inspector(), p.installer(), p.places(), p.xdg(), name -> showMain(Optional.of(name)),
                p.background(), p.ui()));
        window.bind(filePresenter);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closed();
            }
        });
        window.setVisible(true);
        filePresenter.open(file);
    }

    /** Ends the program once no window is left. */
    void closed() {
        p.ui().execute(() -> {
            if (Arrays.stream(Frame.getFrames()).noneMatch(Frame::isDisplayable)) p.exit().run();
        });
    }

    public Optional<MainWindow> mainWindow() {
        return Optional.ofNullable(main);
    }

    public Optional<MainPresenter> mainPresenter() {
        return Optional.ofNullable(presenter);
    }
}
