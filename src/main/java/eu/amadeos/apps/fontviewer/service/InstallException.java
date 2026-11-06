package eu.amadeos.apps.fontviewer.service;

/** Installing or removing a font did not work; the reason picks the message the window shows. */
public final class InstallException extends Exception {
    public enum Reason {
        /** The file is not a font this program can install. */
        NOT_A_FONT,
        /** The password dialog for "all users" was closed. */
        DISMISSED,
        /** polkit refused, or the helper is missing. */
        NOT_AUTHORIZED,
        /** Anything else: disk full, no rights, a failing helper. */
        FAILED
    }

    private final Reason reason;

    public InstallException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public InstallException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
