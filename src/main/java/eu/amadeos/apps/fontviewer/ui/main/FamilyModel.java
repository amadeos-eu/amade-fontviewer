package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFamily;

import java.awt.Font;
import java.util.Optional;

/**
 * What the header and the preview show of the chosen family.
 *
 * @param subtitle     "Sans serif · 18 styles · System font"
 * @param font         the chosen style at size 1, empty when Java cannot draw it
 * @param removable    the user may remove the family here (own fonts, or fonts this program installed for all)
 * @param removeHint   why not, for the tooltip of the disabled button
 */
public record FamilyModel(FontFamily family, FontFace face, String subtitle, Optional<Font> font, boolean removable,
                          String removeHint) {
}
