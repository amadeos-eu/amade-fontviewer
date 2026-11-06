package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.HorizonUI;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * The characters of a block in a grid, drawn in the font. Characters the font does not have are greyed out
 * (drawn in the fallback font). Arrow keys, Home, End, Page Up and Page Down move the selection; Enter or a
 * double-click activates (copies) the selected character. Only the visible rows are painted.
 */
public final class CharacterGrid extends JComponent implements Scrollable {
    public static final int COLUMNS = 12;
    private static final int CELL = 40;

    private int[] codePoints = new int[0];
    private IntPredicate present = cp -> true;
    private Font font = new Font(Font.DIALOG, Font.PLAIN, 22);
    private final Font fallback = new Font(Font.DIALOG, Font.PLAIN, 22);
    private int selected = -1;
    private IntConsumer onSelect = cp -> { };
    private IntConsumer onActivate = cp -> { };

    public CharacterGrid() {
        setName("characterGrid");
        setFocusable(true);
        setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int index = indexAt(e.getX(), e.getY());
                if (index >= 0) {
                    select(index);
                    if (e.getClickCount() == 2) onActivate.accept(codePoints[index]);
                }
            }
        });
        key(KeyEvent.VK_LEFT, -1);
        key(KeyEvent.VK_RIGHT, 1);
        key(KeyEvent.VK_UP, -COLUMNS);
        key(KeyEvent.VK_DOWN, COLUMNS);
        key(KeyEvent.VK_PAGE_UP, -COLUMNS * 8);
        key(KeyEvent.VK_PAGE_DOWN, COLUMNS * 8);
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "first");
        getActionMap().put("first", action(() -> select(0)));
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "last");
        getActionMap().put("last", action(() -> select(codePoints.length - 1)));
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
        getActionMap().put("activate", action(() -> {
            if (selected >= 0) onActivate.accept(codePoints[selected]);
        }));
        AmadeUI.style(this, (c, t) -> {
            c.setBackground(t.fieldBackground());
            c.setForeground(t.fieldText());
        });
    }

    /** Shows these characters; {@code present} says which ones the font really has. */
    public void show(int[] characters, IntPredicate inFont, Font newFont) {
        codePoints = characters.clone();
        present = inFont;
        font = newFont == null ? fallback : newFont.deriveFont(22f);
        selected = -1;
        revalidate();
        repaint();
    }

    public void onSelect(IntConsumer listener) {
        onSelect = listener;
    }

    public void onActivate(IntConsumer listener) {
        onActivate = listener;
    }

    public int count() {
        return codePoints.length;
    }

    /** The selected code point, or -1. */
    public int selectedCodePoint() {
        return selected < 0 ? -1 : codePoints[selected];
    }

    public void select(int index) {
        if (codePoints.length == 0) return;
        selected = Math.max(0, Math.min(codePoints.length - 1, index));
        Rectangle r = cell(selected);
        scrollRectToVisible(r);
        repaint();
        onSelect.accept(codePoints[selected]);
    }

    /** Selects the code point if it is in the grid. */
    public boolean selectCodePoint(int codePoint) {
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == codePoint) {
                select(i);
                return true;
            }
        }
        return false;
    }

    private void key(int code, int delta) {
        String name = "move" + delta;
        getInputMap().put(KeyStroke.getKeyStroke(code, 0), name);
        getActionMap().put(name, action(() -> select(selected < 0 ? 0 : selected + delta)));
    }

    private static AbstractAction action(Runnable r) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
    }

    int indexAt(int x, int y) {
        int col = x / CELL, row = y / CELL;
        if (col < 0 || col >= COLUMNS || row < 0) return -1;
        int index = row * COLUMNS + col;
        return index < codePoints.length ? index : -1;
    }

    private Rectangle cell(int index) {
        return new Rectangle((index % COLUMNS) * CELL, (index / COLUMNS) * CELL, CELL, CELL);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int index = indexAt(e.getX(), e.getY());
        if (index < 0) return null;
        int cp = codePoints[index];
        String name = Character.getName(cp);
        return String.format("U+%04X %s", cp, name == null ? "" : name);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(getBackground());
        Rectangle clip = g.getClipBounds() == null ? new Rectangle(getSize()) : g.getClipBounds();
        g.fill(clip);
        HorizonUI.Palette pal = HorizonUI.Palette.of(AmadeUI.theme());
        Color line = HorizonUI.active() ? pal.line() : AmadeUI.theme().shadow();
        Color accent = HorizonUI.active() ? pal.accent() : AmadeUI.theme().selectionBackground();
        Color onAccent = HorizonUI.active() ? pal.onAccent() : AmadeUI.theme().selectionText();
        int firstRow = Math.max(0, clip.y / CELL), lastRow = (clip.y + clip.height) / CELL + 1;
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = row * COLUMNS + col;
                if (index >= codePoints.length) break;
                int cp = codePoints[index];
                int x = col * CELL, y = row * CELL;
                boolean sel = index == selected;
                if (sel) {
                    g.setColor(accent);
                    g.fillRoundRect(x + 2, y + 2, CELL - 4, CELL - 4, 8, 8);
                }
                g.setColor(line);
                g.drawRect(x, y, CELL, CELL);
                boolean has = present.test(cp);
                Font f = has ? font : fallback;
                g.setFont(f);
                FontMetrics fm = g.getFontMetrics();
                String s = new String(Character.toChars(cp));
                g.setColor(sel ? onAccent : has ? getForeground() : line);
                g.drawString(s, x + (CELL - fm.stringWidth(s)) / 2,
                        y + (CELL + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
        if (isFocusOwner() && selected >= 0) {
            g.setColor(accent);
            Rectangle r = cell(selected);
            g.drawRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        }
        g.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = Math.max(1, (codePoints.length + COLUMNS - 1) / COLUMNS);
        return new Dimension(COLUMNS * CELL + 1, rows * CELL + 1);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(COLUMNS * CELL + 1, 8 * CELL);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return CELL;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? Math.max(CELL, visible.height - CELL) : visible.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
}
