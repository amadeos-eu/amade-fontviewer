package eu.amadeos.apps.fontviewer.domain;

/** Where a font is installed, which decides who sees it and who may remove it. */
public enum FontScope {
    /** In {@code ~/.local/share/fonts} (or the old {@code ~/.fonts}): only this user, removable by them. */
    USER,
    /** In {@code /usr/local/share/fonts}: every user; the administrator removes it. */
    ALL_USERS,
    /** Anywhere else, normally {@code /usr/share/fonts}: came with a package, removed in Software. */
    SYSTEM
}
