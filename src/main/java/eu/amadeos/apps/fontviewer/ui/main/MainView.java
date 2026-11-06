package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.SampleText;
import eu.amadeos.apps.fontviewer.ui.parts.Detail;

import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The main window as the presenter sees it; the tests use a fake. All calls come on the event thread. */
public interface MainView {
    /** The list is being read. */
    void showLoading();

    /** The families that pass the filter and the search; {@code selected} is chosen in the list. */
    void showFamilies(List<FontFamily> families, Optional<FontFamily> selected);

    /** The fonts of the list cells were loaded: paint them again. */
    void refreshList();

    void showFamily(FamilyModel model);

    /** No family chosen (empty list). */
    void showNoFamily();

    void showSample(SampleText kind, String text);

    /** Characters and details, which take a moment to read. */
    void showInspection(String family, Optional<Font> font, Coverage coverage, List<Detail> details);

    /** 0 Preview, 1 Characters, 2 Details. */
    void showPage(int page);

    void showStatus(String text);

    void showError(String message);

    void showInfo(String message);

    /** Asks for whom to install the fonts; empty when cancelled. */
    Optional<InstallTarget> askInstallTarget(List<String> fileNames);

    /** A yes/no question; true for yes. */
    boolean confirm(String message, String yesLabel);

    /** The file chooser for fonts; empty when cancelled. */
    List<Path> chooseFontFiles(boolean multiple);

    void openCompare(List<FontFamily> families, List<String> compared);

    void showAbout();

    void close();
}
