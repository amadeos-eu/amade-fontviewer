package eu.amadeos.apps.fontviewer.ui.file;

import eu.amadeos.apps.fontviewer.ui.parts.Detail;

import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the window of a font file shows.
 *
 * @param title     "Caladea Regular"
 * @param family    the family name, for "Show in Font Viewer"
 * @param state     installed or not, or not a font at all
 * @param stateText "Downloads/Caladea-Regular.ttf · Not installed"
 */
public record FileModel(Path file, String title, String family, Optional<Font> font, State state, String stateText,
                        List<Detail> details) {
    public enum State {
        NOT_INSTALLED, INSTALLED, NOT_A_FONT
    }
}
