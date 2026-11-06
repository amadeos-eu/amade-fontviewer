package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.HorizonUI;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

/** One character, large, with the base line and the x-height of the font as thin lines. */
public final class GlyphView extends JComponent {
    private Font font;
    private int codePoint = -1;

    public GlyphView() {
        setName("glyph");
        setPreferredSize(new Dimension(210, 200));
        AmadeUI.style(this, (c, t) -> {
            c.setBackground(t.fieldBackground());
            c.setForeground(t.fieldText());
        });
    }

    public void show(Font newFont, int newCodePoint) {
        font = newFont;
        codePoint = newCodePoint;
        repaint();
    }

    public int codePoint() {
        return codePoint;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        Color line = HorizonUI.active() ? HorizonUI.palette().line() : AmadeUI.theme().shadow();
        Color accent = HorizonUI.active() ? HorizonUI.palette().accent() : AmadeUI.theme().selectionBackground();
        g.setColor(getBackground());
        g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
        g.setColor(line);
        g.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
        if (font != null && codePoint >= 0) {
            float size = Math.max(24, Math.min(w, h) * 0.6f);
            Font f = font.deriveFont(size);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            String s = new String(Character.toChars(codePoint));
            int base = h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
            Rectangle2D x = f.createGlyphVector(g.getFontRenderContext(), "x").getVisualBounds();
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110));
            g.drawLine(10, base, w - 10, base);
            if (font.canDisplay('x')) g.drawLine(10, base - (int) Math.round(x.getHeight()), w - 10,
                    base - (int) Math.round(x.getHeight()));
            g.setColor(getForeground());
            g.drawString(s, (w - fm.stringWidth(s)) / 2, base);
        }
        g.dispose();
    }
}
