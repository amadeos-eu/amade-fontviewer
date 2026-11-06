package eu.amadeos.apps.fontviewer.ui.file;

import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.FontFiles;
import eu.amadeos.apps.fontviewer.domain.FontScope;
import eu.amadeos.apps.fontviewer.service.FontCatalog;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontInstaller;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.apps.fontviewer.service.FontPlaces;
import eu.amadeos.apps.fontviewer.service.InstallException;
import eu.amadeos.apps.fontviewer.ui.parts.DetailRows;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The logic of the window of one font file, opened from Files: shows the font, says whether it is installed
 * already and installs it for the user or for everyone.
 */
public final class FilePresenter {
    private static final Logger LOG = LoggerFactory.getLogger(FilePresenter.class);

    /** What the presenter works with. */
    public record Services(FontCatalog catalog, FontLoader loader, FontInspector inspector, FontInstaller installer,
                           FontPlaces places, XdgDirectories xdg, Consumer<String> showInViewer,
                           ExecutorService background, Executor ui) {
    }

    private final FileView view;
    private final Messages msg;
    private final Services s;
    private FileModel model;

    public FilePresenter(FileView view, Messages msg, Services services) {
        this.view = view;
        this.msg = msg;
        this.s = services;
    }

    public void open(Path file) {
        Path f = file.toAbsolutePath().normalize();
        s.background().execute(() -> {
            FileModel m = read(f);
            s.ui().execute(() -> {
                model = m;
                view.showFile(m);
            });
        });
    }

    FileModel read(Path file) {
        String shortPath = shortPath(file);
        if (!FontFiles.looksLikeFont(file)) {
            return new FileModel(file, file.getFileName().toString(), "", Optional.empty(), FileModel.State.NOT_A_FONT,
                    shortPath + " · " + msg.get("file.not-a-font"), List.of());
        }
        Optional<Font> font = s.loader().font(file, 0);
        FontInspector.Inspection inspection = s.inspector().inspect(file, 0);
        FontDetails d = inspection.details();
        String family = !d.family().isBlank() ? d.family() : font.map(Font::getFamily).orElse(file.getFileName().toString());
        String style = !d.style().isBlank() ? d.style() : "Regular";
        Optional<FontScope> installed = installedAs(file, family, style);
        String state = shortPath + " · " + installed.map(scope -> switch (scope) {
            case USER -> msg.get("scope.user");
            case ALL_USERS -> msg.get("scope.all-users");
            case SYSTEM -> msg.get("scope.system");
        }).orElse(msg.get("file.not-installed"));
        List<eu.amadeos.apps.fontviewer.ui.parts.Detail> rows = new DetailRows(msg)
                .add("details.style", style)
                .font(d, inspection.coverage(), List.of())
                .more(d)
                .build();
        String title = style.equalsIgnoreCase("Regular") ? family : family + " " + style;
        return new FileModel(file, title, family, font,
                installed.isPresent() ? FileModel.State.INSTALLED : FileModel.State.NOT_INSTALLED, state, rows);
    }

    /** Where a font of the same family and style is installed, if anywhere. */
    private Optional<FontScope> installedAs(Path file, String family, String style) {
        if (s.places().scopeOf(file) != FontScope.SYSTEM || file.startsWith("/usr/share/fonts")) {
            return Optional.of(s.places().scopeOf(file));
        }
        try {
            for (FontFamily f : s.catalog().load()) {
                if (!f.name().equalsIgnoreCase(family)) continue;
                for (FontFace face : f.faces()) {
                    if (face.style().equalsIgnoreCase(style)) return Optional.of(face.scope());
                }
            }
        } catch (IOException e) {
            LOG.info("Could not ask fontconfig: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String shortPath(Path file) {
        Path home = s.xdg().home();
        return file.startsWith(home) ? home.relativize(file).toString() : file.toString();
    }

    public void install(boolean forAll) {
        if (model == null || model.state() == FileModel.State.NOT_A_FONT) return;
        Path file = model.file();
        List<Path> existing = s.installer().existing(List.of(file), forAll);
        if (!existing.isEmpty() && !view.confirm(msg.format("install.replace", file.getFileName()),
                msg.get("install.replace.yes"))) {
            return;
        }
        view.showBusy(msg.get("status.installing"));
        s.background().execute(() -> {
            try {
                if (forAll) s.installer().installForAll(List.of(file));
                else s.installer().installForMe(List.of(file));
                open(file);
            } catch (InstallException e) {
                LOG.warn("Installing {} failed: {}", file, e.getMessage());
                s.ui().execute(() -> {
                    view.showFile(model);
                    switch (e.reason()) {
                        case DISMISSED -> { }
                        case NOT_A_FONT -> view.showError(msg.format("error.not-a-font", e.getMessage()));
                        case NOT_AUTHORIZED -> view.showError(msg.get("error.not-authorized"));
                        case FAILED -> view.showError(msg.format("error.install", e.getMessage()));
                    }
                });
            }
        });
    }

    public void showInViewer() {
        if (model != null) s.showInViewer().accept(model.family());
    }

    public Optional<FileModel> model() {
        return Optional.ofNullable(model);
    }
}
