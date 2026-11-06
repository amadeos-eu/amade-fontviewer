package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.FontFiles;
import eu.amadeos.apps.fontviewer.domain.FontScope;
import eu.amadeos.apps.fontviewer.domain.SampleText;
import eu.amadeos.apps.fontviewer.repository.FontViewerPreferences;
import eu.amadeos.apps.fontviewer.service.FontCatalog;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontInstaller;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.apps.fontviewer.service.FontPlaces;
import eu.amadeos.apps.fontviewer.service.InstallException;
import eu.amadeos.apps.fontviewer.service.PackageOwner;
import eu.amadeos.apps.fontviewer.ui.parts.DetailRows;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The logic of the main window: the font list with filter and search, the chosen family and style, the sample
 * text, installing and removing. Slow work (fontconfig, reading font files, installing) runs on the background
 * executor; everything that touches the view runs on the {@code ui} executor.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);

    /** What the presenter works with. */
    public record Services(FontCatalog catalog, FontLoader loader, FontInspector inspector, FontInstaller installer,
                           PackageOwner owner, FontPlaces places, FontViewerPreferences prefs, Opener opener,
                           Consumer<Path> openFile, ExecutorService background, Executor ui, Runnable exit) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final Services s;
    private List<FontFamily> all = List.of();
    private List<FontFamily> visible = List.of();
    private Filter filter = Filter.ALL;
    private String query = "";
    private FontFamily family;
    private FontFace face;
    private SampleText sample;
    private String ownText;
    private int page;
    private int loads;

    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.s = services;
        this.sample = services.prefs().sample();
        this.ownText = services.prefs().ownText();
        this.page = services.prefs().page();
    }

    // ---------------------------------------------------------------- loading

    /** Reads the font list and chooses the family shown last (or {@code select}, when given). */
    public void start(Optional<String> select) {
        view.showPage(page);
        view.showSample(sample, sampleText());
        load(select.or(() -> s.prefs().lastFamily()));
    }

    /** Reads the list again (a font may have been installed meanwhile) and chooses the family. */
    public void reveal(String familyName) {
        filter = Filter.ALL;
        query = "";
        load(Optional.of(familyName));
    }

    public void reload() {
        load(Optional.ofNullable(family).map(FontFamily::name));
    }

    private void load(Optional<String> select) {
        int load = ++loads;
        view.showLoading();
        view.showStatus(msg.get("status.loading"));
        s.background().execute(() -> {
            try {
                List<FontFamily> families = s.catalog().load();
                s.ui().execute(() -> {
                    if (load == loads) loaded(families, select);
                });
                preload(families);
            } catch (IOException e) {
                LOG.warn("Could not list the fonts", e);
                s.ui().execute(() -> view.showError(msg.format("error.list", e.getMessage())));
            }
        });
    }

    private void loaded(List<FontFamily> families, Optional<String> select) {
        all = families;
        Optional<FontFamily> wanted = select.flatMap(this::find);
        refreshList(wanted.or(() -> visible.isEmpty() ? Optional.empty() : Optional.of(visible.getFirst())));
    }

    /** Loads the regular style of each family, so that the list can show each name in its own font. */
    private void preload(List<FontFamily> families) {
        int n = 0;
        for (FontFamily f : families) {
            s.loader().font(f.regular());
            if (++n % 40 == 0) s.ui().execute(view::refreshList);
        }
        s.ui().execute(view::refreshList);
    }

    public Optional<FontFamily> find(String name) {
        return all.stream().filter(f -> f.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<FontFamily> families() {
        return all;
    }

    // ---------------------------------------------------------------- list

    public void setFilter(Filter newFilter) {
        filter = newFilter;
        refreshList(Optional.ofNullable(family));
    }

    public void setQuery(String text) {
        query = text == null ? "" : text.strip();
        refreshList(Optional.ofNullable(family));
    }

    private void refreshList(Optional<FontFamily> keep) {
        String q = query.toLowerCase(Locale.ROOT);
        visible = all.stream().filter(filter::accepts)
                .filter(f -> q.isEmpty() || f.name().toLowerCase(Locale.ROOT).contains(q)).toList();
        Optional<FontFamily> chosen = keep.flatMap(k -> visible.stream().filter(f -> f.name().equals(k.name()))
                .findFirst());
        if (chosen.isEmpty() && !visible.isEmpty()) chosen = Optional.of(visible.getFirst());
        view.showFamilies(visible, chosen);
        long mine = all.stream().filter(FontFamily::mine).count();
        view.showStatus(visible.size() == all.size()
                ? msg.format("status.families", all.size(), mine)
                : msg.format("status.families.filtered", visible.size(), all.size()));
        if (chosen.isPresent()) {
            if (family == null || !family.equals(chosen.get())) select(chosen.get());
        } else {
            family = null;
            face = null;
            view.showNoFamily();
        }
    }

    // ---------------------------------------------------------------- family and style

    public void select(FontFamily newFamily) {
        family = newFamily;
        face = newFamily.regular();
        s.prefs().setLastFamily(newFamily.name());
        showFace();
    }

    public void selectStyle(String style) {
        if (family == null) return;
        face = family.face(style);
        showFace();
    }

    private void showFace() {
        FontFamily f = family;
        FontFace current = face;
        Optional<Font> font = s.loader().font(current);
        view.showFamily(model(f, current, font, Optional.empty()));
        s.background().execute(() -> {
            FontInspector.Inspection inspection = s.inspector().inspect(current.file(), current.index());
            Optional<String> pkg = f.scope() == FontScope.SYSTEM ? s.owner().packageOf(current.file()) : Optional.empty();
            s.ui().execute(() -> {
                if (current != face) return;
                if (pkg.isPresent()) view.showFamily(model(f, current, font, pkg));
                List<eu.amadeos.apps.fontviewer.ui.parts.Detail> rows = new DetailRows(msg)
                        .add("details.styles", f.faces().stream().map(FontFace::style).distinct()
                                .collect(Collectors.joining(", ")))
                        .font(inspection.details(), inspection.coverage(), current.languages())
                        .add("details.installed", scopeText(f, pkg))
                        .add("details.files", f.faces().stream().map(x -> x.file().toString()).distinct()
                                .collect(Collectors.joining("\n")))
                        .more(inspection.details())
                        .build();
                view.showInspection(f.name(), font, inspection.coverage(), rows);
            });
        });
    }

    FamilyModel model(FontFamily f, FontFace current, Optional<Font> font, Optional<String> pkg) {
        int styles = f.faces().size();
        String subtitle = msg.get(f.category().textKey()) + " · "
                + (styles == 1 ? msg.get("list.style") : msg.format("list.styles", styles)) + " · " + scopeText(f, pkg);
        boolean removable = removable(f);
        String hint = removable ? "" : msg.get("remove.system");
        return new FamilyModel(f, current, subtitle, font, removable, hint);
    }

    private String scopeText(FontFamily f, Optional<String> pkg) {
        return switch (f.scope()) {
            case USER -> msg.get("scope.user");
            case ALL_USERS -> msg.get("scope.all-users");
            case SYSTEM -> pkg.map(p -> msg.format("scope.package", p)).orElse(msg.get("scope.system"));
        };
    }

    boolean removable(FontFamily f) {
        return f.faces().stream().anyMatch(x -> x.scope() == FontScope.USER || s.places().removableForAll(x.file()));
    }

    public Optional<FontFamily> family() {
        return Optional.ofNullable(family);
    }

    public Optional<FontFace> face() {
        return Optional.ofNullable(face);
    }

    // ---------------------------------------------------------------- sample and pages

    public void setSample(SampleText kind) {
        sample = kind;
        s.prefs().setSample(kind);
        view.showSample(kind, sampleText());
    }

    /** The user typed in the sample field: that is now the own text. */
    public void setOwnText(String text) {
        ownText = text;
        s.prefs().setOwnText(text);
        if (sample != SampleText.OWN) {
            sample = SampleText.OWN;
            s.prefs().setSample(sample);
        }
        view.showSample(sample, sampleText());
    }

    public String sampleText() {
        return switch (sample) {
            case SENTENCE -> msg.get("sample.sentence.text");
            case ALPHABET -> SampleText.ALPHABET_TEXT;
            case NUMBERS -> SampleText.NUMBERS_TEXT;
            case PARAGRAPH -> msg.get("sample.paragraph.text");
            case OWN -> ownText.isBlank() ? msg.get("sample.sentence.text") : ownText;
        };
    }

    public SampleText sample() {
        return sample;
    }

    public void setPage(int newPage) {
        page = newPage;
        s.prefs().setPage(newPage);
        view.showPage(newPage);
    }

    // ---------------------------------------------------------------- compare

    public void addToCompare() {
        if (family == null) return;
        List<String> list = new ArrayList<>(s.prefs().compared());
        list.remove(family.name());
        list.addFirst(family.name());
        while (list.size() > FontViewerPreferences.COMPARE_MAX) list.removeLast();
        s.prefs().setCompared(list);
        openCompare();
    }

    public void openCompare() {
        List<String> names = s.prefs().compared().stream().filter(n -> find(n).isPresent()).toList();
        if (names.isEmpty() && family != null) names = List.of(family.name());
        view.openCompare(all, names);
    }

    // ---------------------------------------------------------------- install and remove

    public void installFonts() {
        List<Path> files = view.chooseFontFiles(true);
        if (!files.isEmpty()) install(files);
    }

    /** Installs dropped or chosen files: asks for whom, warns about replacing, then reloads the list. */
    public void install(Collection<Path> files) {
        List<Path> fonts = files.stream().filter(FontFiles::hasFontExtension).toList();
        if (fonts.isEmpty()) {
            view.showError(msg.get("error.no-fonts"));
            return;
        }
        Optional<InstallTarget> target = view.askInstallTarget(fonts.stream()
                .map(p -> p.getFileName().toString()).toList());
        if (target.isEmpty()) return;
        boolean forAll = target.get() == InstallTarget.FOR_ALL;
        List<Path> existing = s.installer().existing(fonts, forAll);
        if (!existing.isEmpty() && !view.confirm(msg.format("install.replace", existing.stream()
                .map(p -> p.getFileName().toString()).collect(Collectors.joining(", "))), msg.get("install.replace.yes"))) {
            return;
        }
        view.showStatus(msg.get("status.installing"));
        s.background().execute(() -> {
            try {
                List<Path> installed = forAll ? s.installer().installForAll(fonts) : s.installer().installForMe(fonts);
                installed.forEach(p -> {
                    s.loader().forget(p);
                    s.inspector().forget(p);
                });
                Optional<String> name = installed.stream().findFirst().flatMap(this::familyNameOf);
                s.ui().execute(() -> {
                    view.showInfo(msg.format(forAll ? "install.done.all" : "install.done", installed.size()));
                    load(name);
                });
            } catch (InstallException e) {
                s.ui().execute(() -> installFailed(e));
            }
        });
    }

    /** The family name the file says it belongs to, to choose it after installing. */
    private Optional<String> familyNameOf(Path file) {
        return s.loader().font(file, 0).map(Font::getFamily);
    }

    private void installFailed(InstallException e) {
        LOG.warn("Installing or removing failed: {}", e.getMessage());
        view.showStatus(" ");
        switch (e.reason()) {
            case DISMISSED -> view.showStatus(msg.get("status.cancelled"));
            case NOT_A_FONT -> view.showError(msg.format("error.not-a-font", e.getMessage()));
            case NOT_AUTHORIZED -> view.showError(msg.get("error.not-authorized"));
            case FAILED -> view.showError(msg.format("error.install", e.getMessage()));
        }
    }

    public void removeFamily() {
        if (family == null) return;
        FontFamily f = family;
        if (!removable(f)) {
            view.showInfo(msg.get("remove.system"));
            return;
        }
        List<FontFace> faces = f.faces().stream()
                .filter(x -> x.scope() == FontScope.USER || s.places().removableForAll(x.file())).toList();
        if (!view.confirm(msg.format("remove.confirm", f.name(), faces.size()), msg.get("remove.yes"))) return;
        view.showStatus(msg.get("status.removing"));
        s.background().execute(() -> {
            try {
                List<Path> removed = s.installer().remove(faces);
                removed.forEach(p -> {
                    s.loader().forget(p);
                    s.inspector().forget(p);
                });
                s.ui().execute(() -> {
                    view.showInfo(msg.format("remove.done", f.name()));
                    family = null;
                    load(Optional.empty());
                });
            } catch (InstallException e) {
                s.ui().execute(() -> installFailed(e));
            }
        });
    }

    // ---------------------------------------------------------------- the rest

    public void openFontFile() {
        view.chooseFontFiles(false).stream().findFirst().ifPresent(s.openFile());
    }

    public void showInFolder() {
        if (face != null) open(() -> s.opener().showInFolder(face.file()));
    }

    public void getMoreFonts() {
        open(s.opener()::getMoreFonts);
    }

    public void handbook() {
        open(s.opener()::handbook);
    }

    private interface Opening {
        void run() throws IOException;
    }

    private void open(Opening opening) {
        try {
            opening.run();
        } catch (IOException e) {
            view.showError(msg.format("error.open", e.getMessage()));
        }
    }

    public void about() {
        view.showAbout();
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        view.showStatus(Messages.commons(msg.locale()).get("language.restart"));
    }

    public void exit() {
        view.close();
        s.exit().run();
    }
}
