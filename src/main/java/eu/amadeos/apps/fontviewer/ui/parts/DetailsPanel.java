package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** Label and value lines, values selectable so that a licence link or a path can be copied. */
public final class DetailsPanel extends JPanel implements javax.swing.Scrollable {
    private final List<Detail> shown = new ArrayList<>();

    public DetailsPanel() {
        super(new GridBagLayout());
        setName("details");
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
    }

    public void show(List<Detail> details) {
        removeAll();
        shown.clear();
        shown.addAll(details);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(3, 0, 3, 16);
        int row = 0;
        for (Detail d : details) {
            c.gridy = row++;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            JLabel label = new JLabel(d.label());
            AmadeUI.style(label, (l, t) -> l.setForeground(t.textInactive()));
            add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            JTextArea value = new JTextArea(d.value());
            value.setName("detail." + (row - 1));
            value.setEditable(false);
            value.setOpaque(false);
            value.setBorder(null);
            value.setLineWrap(true);
            value.setWrapStyleWord(true);
            value.setFont(label.getFont());
            add(value, c);
        }
        c.gridy = row;
        c.weighty = 1;
        add(Box.createGlue(), c);
        revalidate();
        repaint();
    }

    public List<Detail> details() {
        return List.copyOf(shown);
    }

    @Override
    public java.awt.Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
        return Math.max(16, visible.height - 16);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
}
