package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Optional;
import java.util.function.Function;

/**
 * A row of the font list: the family's name in its own font (once the font is loaded; until then in the
 * normal one), below it the kind, the number of styles and "yours" for the user's own fonts.
 */
final class FamilyCellRenderer extends JComponent implements ListCellRenderer<FontFamily> {
    private final Messages msg;
    private final Function<FontFamily, Optional<Font>> fonts;
    private FontFamily family;
    private boolean selected;
    private Color background;
    private Color foreground;

    FamilyCellRenderer(Messages msg, Function<FontFamily, Optional<Font>> fonts) {
        this.msg = msg;
        this.fonts = fonts;
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.LABEL;
                }
            };
        }
        return accessibleContext;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends FontFamily> list, FontFamily value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        family = value;
        selected = isSelected;
        background = isSelected ? list.getSelectionBackground() : list.getBackground();
        foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
        setFont(list.getFont());
        getAccessibleContext().setAccessibleName(value == null ? "" : value.name());
        return this;
    }

    String secondLine(FontFamily f) {
        int styles = f.faces().size();
        String line = msg.get(f.category().textKey()) + " · "
                + (styles == 1 ? msg.get("list.style") : msg.format("list.styles", styles));
        return f.mine() ? line + " · " + msg.get("list.yours") : line;
    }

    @Override
    public Dimension getPreferredSize() {
        float base = getFont() == null ? 13f : getFont().getSize2D();
        return new Dimension(220, Math.round(base * 3.4f + 6));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (family == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(background);
        g.fillRect(0, 0, getWidth(), getHeight());
        Font ui = getFont();
        float base = ui.getSize2D();
        Font name = fonts.apply(family).filter(f -> f.canDisplayUpTo(family.name()) < 0)
                .map(f -> f.deriveFont(base * 1.3f)).orElse(ui.deriveFont(base * 1.2f));
        g.setFont(name);
        g.setColor(foreground);
        int top = Math.round(base * 1.55f);
        g.drawString(family.name(), 10, top);
        g.setFont(ui.deriveFont(base * 0.85f));
        Color dim = AmadeUI.theme() == null ? foreground : AmadeUI.theme().textInactive();
        g.setColor(selected ? foreground : dim);
        g.drawString(secondLine(family), 10, top + Math.round(base * 1.35f));
        g.dispose();
    }
}
