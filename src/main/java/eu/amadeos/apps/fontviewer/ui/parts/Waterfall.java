package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

/**
 * The sample text in several sizes, one below the other, each with its size in points on the left. Long texts
 * wrap at the width of the view, so a paragraph shows how the font reads as running text.
 */
public final class Waterfall extends JComponent implements Scrollable {
    public static final int[] SIZES = {12, 16, 20, 28, 36, 48, 64};
    private static final int LEFT = 62, GAP = 14, MARGIN = 12;

    private Font font;
    private String text = "";
    private int[] sizes = SIZES;
    private String message = "";

    public Waterfall() {
        setOpaque(true);
        setName("waterfall");
        AmadeUI.style(this, (c, t) -> {
            c.setBackground(t.fieldBackground());
            c.setForeground(t.fieldText());
        });
    }

    /** The font at size 1 (any size; it is derived), or null for "cannot show", with {@code message} instead. */
    public void show(Font newFont, String newText, String newMessage) {
        font = newFont;
        text = newText == null ? "" : newText;
        message = newMessage == null ? "" : newMessage;
        revalidate();
        repaint();
    }

    public void setSizes(int... newSizes) {
        sizes = newSizes.clone();
        revalidate();
        repaint();
    }

    public String text() {
        return text;
    }

    public Font shownFont() {
        return font;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        layout(g, true);
        g.dispose();
    }

    /** Lays the lines out and, when {@code paint}, draws them; returns the height used. */
    private int layout(Graphics2D g, boolean paint) {
        int y = MARGIN;
        Font label = getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 11) : getFont().deriveFont(11f);
        if (font == null || text.isEmpty()) {
            if (paint && !message.isEmpty()) {
                g.setFont(getFont());
                g.setColor(getForeground());
                g.drawString(message, MARGIN, y + g.getFontMetrics().getAscent());
            }
            return y + 30;
        }
        FontRenderContext frc = g.getFontRenderContext();
        float width = Math.max(100, (getWidth() > 0 ? getWidth() : 640) - LEFT - MARGIN);
        for (int size : sizes) {
            Font f = font.deriveFont((float) size);
            List<TextLayout> lines = lines(f, frc, width);
            boolean first = true;
            for (TextLayout line : lines) {
                y += Math.round(line.getAscent());
                if (paint) {
                    if (first) {
                        g.setFont(label);
                        g.setColor(AmadeUI.theme() == null ? getForeground() : AmadeUI.theme().textInactive());
                        g.drawString(size + " pt", MARGIN, y);
                    }
                    g.setColor(getForeground());
                    line.draw(g, LEFT, y);
                }
                y += Math.round(line.getDescent() + line.getLeading());
                first = false;
            }
            y += GAP;
        }
        return y;
    }

    private List<TextLayout> lines(Font f, FontRenderContext frc, float width) {
        List<TextLayout> result = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            if (paragraph.isEmpty()) paragraph = " ";
            AttributedString s = new AttributedString(paragraph);
            s.addAttribute(TextAttribute.FONT, f);
            LineBreakMeasurer measurer = new LineBreakMeasurer(s.getIterator(), frc);
            while (measurer.getPosition() < paragraph.length()) {
                result.add(measurer.nextLayout(width));
            }
        }
        return result;
    }

    @Override
    public Dimension getPreferredSize() {
        Graphics2D g = (Graphics2D) new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB).getGraphics();
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        int h = layout(g, false);
        g.dispose();
        return new Dimension(400, h);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(560, 380);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height - 24 : visible.width;
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
