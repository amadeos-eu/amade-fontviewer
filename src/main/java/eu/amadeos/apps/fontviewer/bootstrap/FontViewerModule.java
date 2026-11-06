package eu.amadeos.apps.fontviewer.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.fontviewer.repository.FontViewerPreferences;
import eu.amadeos.apps.fontviewer.service.DesktopOpener;
import eu.amadeos.apps.fontviewer.service.FontCatalog;
import eu.amadeos.apps.fontviewer.service.FontInspector;
import eu.amadeos.apps.fontviewer.service.FontInstaller;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.apps.fontviewer.service.FontPlaces;
import eu.amadeos.apps.fontviewer.service.PackageOwner;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * amade-fontviewer bindings. Theme, language, directories and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class FontViewerModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.fontviewer.messages";

    @Override
    protected void configure() {
        bind(FontViewerPreferences.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(FontViewerModule.class, MESSAGES, locale);
    }

    // --8<-- [start:windows]
    @Provides
    @Singleton
    Windows windows(Messages msg, LocaleSettings locales, FontViewerPreferences prefs, XdgDirectories xdg,
                    DesktopLauncher launcher, ExecutorService background, @Ui Executor ui) {
        CommandRunner runner = CommandRunner.system();
        FontPlaces places = FontPlaces.of(xdg);
        FontLoader loader = new FontLoader();
        return new Windows(new Windows.Parts(msg, locales, new FontCatalog(runner, places), loader,
                new FontInspector(loader), new FontInstaller(runner, places), new PackageOwner(runner), places, prefs,
                new DesktopOpener(launcher, runner), xdg, background, ui, () -> System.exit(0)));
    }
    // --8<-- [end:windows]
}
