package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.domain.FontScope;
import eu.amadeos.commons.config.XdgDirectories;

import java.nio.file.Path;

/**
 * The font folders: the user's own ({@code $XDG_DATA_HOME/fonts}, also read: the old {@code ~/.fonts}),
 * the one for all users that this program fills ({@code /usr/local/share/fonts/amadeos}) and the rest.
 */
public record FontPlaces(Path userFonts, Path oldUserFonts, Path allUsersFonts) {
    /** The sub-folder of {@code /usr/local/share/fonts} that "Install for all users" uses. */
    public static final Path ALL_USERS = Path.of("/usr/local/share/fonts/amadeos");

    public static FontPlaces of(XdgDirectories xdg) {
        return new FontPlaces(xdg.dataHome().resolve("fonts"), xdg.home().resolve(".fonts"), ALL_USERS);
    }

    public FontScope scopeOf(Path file) {
        Path f = file.toAbsolutePath().normalize();
        if (f.startsWith(userFonts) || f.startsWith(oldUserFonts)) return FontScope.USER;
        if (f.startsWith(allUsersFonts.getParent())) return FontScope.ALL_USERS;
        return FontScope.SYSTEM;
    }

    /** True when this program may remove the file for all users (only from its own folder). */
    public boolean removableForAll(Path file) {
        return file.toAbsolutePath().normalize().startsWith(allUsersFonts);
    }
}
