package eu.amadeos.apps.fontviewer.ui.file;

/** The window of one font file as its presenter sees it. */
public interface FileView {
    void showFile(FileModel model);

    /** The installation is running; the buttons wait. */
    void showBusy(String text);

    void showError(String message);

    boolean confirm(String message, String yesLabel);

    void close();
}
