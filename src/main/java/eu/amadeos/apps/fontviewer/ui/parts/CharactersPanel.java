package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.UnicodeBlocks;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The Characters page: a Unicode block (or all characters of the font, or the hits of a search) in a grid,
 * the chosen character large with its code and name, and buttons to copy it.
 */
public final class CharactersPanel extends JPanel {
    /** An entry of the "Show" box: a block, or null for all characters of the font. */
    record BlockChoice(UnicodeBlocks.Block block, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final int SEARCH_LIMIT = 1200;

    private final Messages msg;
    private final JComboBox<BlockChoice> blocks = new JComboBox<>();
    private final JTextField find = new JTextField(14);
    private final CharacterGrid grid = new CharacterGrid();
    private final GlyphView glyph = new GlyphView();
    private final JLabel code = new JLabel(" ");
    private final JLabel summary = new JLabel(" ");
    private final JButton copy = new JButton();
    private final JButton copyCode = new JButton();
    private Coverage coverage = Coverage.EMPTY;
    private Font font;
    private String family = "";
    private boolean updating;

    public CharactersPanel(Messages msg) {
        super(new BorderLayout(10, 8));
        this.msg = msg;
        setName("charactersPanel");
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel show = new JLabel(msg.get("characters.show"));
        show.setLabelFor(blocks);
        bar.add(show);
        blocks.setName("blocks");
        blocks.setMaximumRowCount(20);
        blocks.addActionListener(e -> {
            if (!updating) showBlock();
        });
        bar.add(blocks);
        JLabel findLabel = new JLabel(msg.get("characters.find"));
        findLabel.setLabelFor(find);
        bar.add(javax.swing.Box.createHorizontalStrut(8));
        bar.add(findLabel);
        find.setName("find");
        find.setToolTipText(msg.get("characters.find.tip"));
        find.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                search();
            }
        });
        bar.add(find);
        add(bar, BorderLayout.NORTH);

        add(new JScrollPane(grid), BorderLayout.CENTER);
        grid.onSelect(this::showCharacter);
        grid.onActivate(cp -> copyCharacter());

        JPanel side = new JPanel(new BorderLayout(0, 8));
        side.setPreferredSize(new Dimension(220, 300));
        side.add(glyph, BorderLayout.NORTH);
        code.setName("code");
        code.setVerticalAlignment(JLabel.TOP);
        side.add(code, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        Mnemonics.apply(copy, msg.get("characters.copy"));
        copy.setName("copy");
        copy.addActionListener(e -> copyCharacter());
        Mnemonics.apply(copyCode, msg.get("characters.copy-code"));
        copyCode.setName("copyCode");
        copyCode.addActionListener(e -> copyCode());
        buttons.add(copy);
        buttons.add(copyCode);
        side.add(buttons, BorderLayout.SOUTH);
        add(side, BorderLayout.EAST);

        summary.setName("summary");
        summary.setFont(summary.getFont().deriveFont(summary.getFont().getSize2D() * 0.92f));
        add(summary, BorderLayout.SOUTH);
        showCharacter(-1);
    }

    /** Shows the characters of this font; keeps the chosen block when the new font has it too. */
    public void show(String familyName, Font newFont, Coverage newCoverage) {
        family = familyName;
        font = newFont;
        coverage = newCoverage;
        BlockChoice previous = (BlockChoice) blocks.getSelectedItem();
        updating = true;
        DefaultComboBoxModel<BlockChoice> model = new DefaultComboBoxModel<>();
        model.addElement(new BlockChoice(null, msg.get("characters.all")));
        BlockChoice keep = null;
        BlockChoice latin1 = null;
        for (UnicodeBlocks.Block b : coverage.blocks()) {
            BlockChoice c = new BlockChoice(b, b.displayName());
            model.addElement(c);
            if (previous != null && previous.block() != null && previous.block().equals(b)) keep = c;
            if (b.id().equals("LATIN_1_SUPPLEMENT")) latin1 = c;
        }
        blocks.setModel(model);
        blocks.setSelectedItem(keep != null ? keep : latin1 != null ? latin1 : model.getElementAt(0));
        updating = false;
        summary.setText(msg.format("characters.summary", family, coverage.size(), coverage.blocks().size()));
        if (find.getText().isBlank()) showBlock();
        else search();
    }

    private void showBlock() {
        BlockChoice choice = (BlockChoice) blocks.getSelectedItem();
        int[] shown = choice == null || choice.block() == null ? coverage.stream().toArray()
                : IntStream.rangeClosed(choice.block().start(), choice.block().end())
                .filter(cp -> Character.isDefined(cp) && !Character.isISOControl(cp)).toArray();
        grid.show(shown, coverage::contains, font);
        if (grid.count() > 0) grid.select(0);
        else showCharacter(-1);
    }

    private void search() {
        String text = find.getText().strip();
        if (text.isEmpty()) {
            showBlock();
            return;
        }
        int[] hits = matches(text, coverage);
        grid.show(hits, coverage::contains, font);
        if (hits.length > 0) grid.select(0);
        else showCharacter(-1);
    }

    /**
     * The characters of the font that match: the character itself ("ß"), its code ("U+00DF") or all words of its
     * Unicode name ("sharp s").
     */
    static int[] matches(String query, Coverage coverage) {
        String q = query.strip();
        List<Integer> result = new ArrayList<>();
        Optional<Integer> code = codeOf(q);
        code.filter(coverage::contains).ifPresent(result::add);
        if (q.codePointCount(0, q.length()) == 1 && coverage.contains(q.codePointAt(0))
                && !result.contains(q.codePointAt(0))) {
            result.add(q.codePointAt(0));
        }
        String[] words = q.toUpperCase(Locale.ROOT).split("\\s+");
        if (q.length() >= 2) {
            coverage.stream().filter(cp -> {
                String name = Character.getName(cp);
                if (name == null) return false;
                for (String w : words) if (!name.contains(w)) return false;
                return true;
            }).limit(SEARCH_LIMIT).forEach(cp -> {
                if (!result.contains(cp)) result.add(cp);
            });
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    /** "U+00DF", "0xDF" or four to six hex digits ("00df") as a code point. */
    static Optional<Integer> codeOf(String q) {
        String s = q.strip().toUpperCase(Locale.ROOT);
        String hex;
        if (s.startsWith("U+") || s.startsWith("0X")) hex = s.substring(2);
        else if (s.matches("[0-9A-F]{4,6}")) hex = s;
        else return Optional.empty();
        if (!hex.matches("[0-9A-F]{1,6}")) return Optional.empty();
        int cp = Integer.parseInt(hex, 16);
        return cp <= Character.MAX_CODE_POINT ? Optional.of(cp) : Optional.empty();
    }

    private void showCharacter(int cp) {
        glyph.show(cp >= 0 && coverage.contains(cp) ? font : null, cp);
        copy.setEnabled(cp >= 0);
        copyCode.setEnabled(cp >= 0);
        if (cp < 0) {
            code.setText(" ");
            return;
        }
        String name = Character.getName(cp);
        StringBuilder b = new StringBuilder("<html><b>").append(String.format("U+%04X", cp)).append("</b><br>")
                .append(name == null ? "" : escape(name)).append("<br><br>")
                .append(msg.format("characters.html", "&amp;#x" + Integer.toHexString(cp).toUpperCase(Locale.ROOT) + ";"));
        if (!coverage.contains(cp)) b.append("<br><br><i>").append(escape(msg.get("characters.missing"))).append("</i>");
        code.setText(b.append("</html>").toString());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** Puts the selected character on the clipboard. */
    public void copyCharacter() {
        int cp = grid.selectedCodePoint();
        if (cp < 0) return;
        clip(new String(Character.toChars(cp)));
    }

    private void copyCode() {
        int cp = grid.selectedCodePoint();
        if (cp >= 0) clip(String.format("U+%04X", cp));
    }

    private static void clip(String s) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
        } catch (IllegalStateException | java.awt.HeadlessException e) {
            // the clipboard is busy or there is none
        }
    }

    public void focusSearch() {
        find.requestFocusInWindow();
        find.selectAll();
    }

    // ---- for tests and screenshots

    public CharacterGrid grid() {
        return grid;
    }

    public JComboBox<BlockChoice> blocks() {
        return blocks;
    }

    public JTextField findField() {
        return find;
    }

    public String codeText() {
        return code.getText();
    }

    public String summaryText() {
        return summary.getText();
    }
}
