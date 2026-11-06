package eu.amadeos.apps.fontviewer.ui.compare;

import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.repository.FontViewerPreferences;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.HorizonUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Up to six fonts one below the other with the same text at the same size, each with its own style. The
 * families and the size are remembered.
 */
public final class CompareWindow extends JFrame {
    private final Messages msg;
    private final FontLoader loader;
    private final FontViewerPreferences prefs;
    private final JTextField text = new JTextField();
    private final JSlider size = new JSlider(8, 144, 28);
    private final JLabel sizeLabel = new JLabel();
    private final JPanel rows = new JPanel();
    private final JButton add = new JButton();
    private final List<Row> shown = new ArrayList<>();
    private List<FontFamily> families = List.of();

    /** One font of the comparison. */
    final class Row {
        final FontFamily family;
        final JComboBox<String> style = new JComboBox<>();
        final JLabel sample = new JLabel();
        final JPanel panel = new JPanel(new BorderLayout(10, 2));

        Row(FontFamily family) {
            this.family = family;
            for (FontFace f : family.faces()) style.addItem(f.style());
            style.setSelectedItem(family.regular().style());
            style.setEnabled(family.faces().size() > 1);
            style.addActionListener(e -> update());
            JLabel title = new JLabel("<html><b>" + family.name() + "</b> &nbsp;" + msg.get(family.category().textKey())
                    + (family.mine() ? " · " + msg.get("list.yours") : "") + "</html>");
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(title, BorderLayout.WEST);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.setOpaque(false);
            buttons.add(style);
            JButton remove = new JButton("×");
            remove.setName("remove." + family.name());
            remove.setToolTipText(msg.get("compare.remove"));
            remove.addActionListener(e -> removeRow(this));
            buttons.add(remove);
            head.add(buttons, BorderLayout.EAST);
            panel.add(head, BorderLayout.NORTH);
            sample.setName("sample." + family.name());
            panel.add(sample, BorderLayout.CENTER);
            AmadeUI.style(panel, (p, t) -> {
                p.setOpaque(true);
                p.setBackground(t.fieldBackground());
                title.setForeground(t.fieldText());
                sample.setForeground(t.fieldText());
            });
            panel.setBorder(BorderFactory.createCompoundBorder(
                    HorizonUI.active() ? new HorizonUI.LineBorder(true) : AmadeUI.sunken(),
                    BorderFactory.createEmptyBorder(6, 10, 8, 10)));
            panel.setAlignmentX(0);
        }

        void update() {
            FontFace face = family.face((String) style.getSelectedItem());
            Optional<Font> font = loader.font(face);
            sample.setText(text.getText().isEmpty() ? " " : text.getText());
            sample.setFont(font.map(f -> f.deriveFont((float) size.getValue()))
                    .orElse(sample.getFont().deriveFont((float) size.getValue())));
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        }
    }

    public CompareWindow(Messages msg, FontLoader loader, FontViewerPreferences prefs) {
        super(msg.get("compare.title"));
        this.msg = msg;
        this.loader = loader;
        this.prefs = prefs;
        setName("compare");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-fontviewer.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        text.setName("compareText");
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateAll();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateAll();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateAll();
            }
        });
        bar.add(text, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(new JLabel(msg.get("compare.size")));
        size.setName("size");
        size.setValue(prefs.compareSize());
        size.setPreferredSize(new Dimension(160, size.getPreferredSize().height));
        size.addChangeListener(e -> {
            sizeLabel.setText(msg.format("compare.points", size.getValue()));
            if (!size.getValueIsAdjusting()) prefs.setCompareSize(size.getValue());
            updateAll();
        });
        sizeLabel.setText(msg.format("compare.points", size.getValue()));
        right.add(size);
        right.add(sizeLabel);
        bar.add(right, BorderLayout.EAST);
        p.add(bar, BorderLayout.NORTH);

        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        p.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        Mnemonics.apply(add, msg.get("compare.add"));
        add.setName("add");
        add.addActionListener(e -> chooseFamily());
        bottom.add(add, BorderLayout.WEST);
        JLabel hint = new JLabel(msg.format("compare.hint", FontViewerPreferences.COMPARE_MAX));
        AmadeUI.style(hint, (l, t) -> l.setForeground(t.textInactive()));
        bottom.add(hint, BorderLayout.EAST);
        p.add(bottom, BorderLayout.SOUTH);
        add(p);
        setSize(Math.round(900 * AmadeUI.fontScale()), Math.round(580 * AmadeUI.fontScale()));
        setLocationByPlatform(true);
    }

    /** Shows these families; the text is the sample of the main window. */
    public void show(List<FontFamily> allFamilies, List<String> names, String sample) {
        families = allFamilies;
        if (text.getText().isEmpty() || shown.isEmpty()) text.setText(sample.lines().findFirst().orElse(sample));
        shown.clear();
        rows.removeAll();
        for (String n : names) {
            allFamilies.stream().filter(f -> f.name().equals(n)).findFirst().ifPresent(this::addRow);
        }
        refresh();
    }

    private void addRow(FontFamily family) {
        if (shown.size() >= FontViewerPreferences.COMPARE_MAX) return;
        if (shown.stream().anyMatch(r -> r.family.name().equals(family.name()))) return;
        Row row = new Row(family);
        shown.add(row);
        if (rows.getComponentCount() > 0) rows.add(Box.createVerticalStrut(8));
        rows.add(row.panel);
        row.update();
    }

    private void removeRow(Row row) {
        shown.remove(row);
        rows.removeAll();
        List<Row> keep = List.copyOf(shown);
        shown.clear();
        for (Row r : keep) addRow(r.family);
        save();
        refresh();
    }

    private void chooseFamily() {
        if (shown.size() >= FontViewerPreferences.COMPARE_MAX) {
            JOptionPane.showMessageDialog(this, msg.format("compare.full", FontViewerPreferences.COMPARE_MAX));
            return;
        }
        JComboBox<String> box = new JComboBox<>(families.stream().map(FontFamily::name)
                .filter(n -> shown.stream().noneMatch(r -> r.family.name().equals(n))).toArray(String[]::new));
        box.setMaximumRowCount(20);
        if (JOptionPane.showConfirmDialog(this, box, Mnemonics.strip(msg.get("compare.add")),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String name = (String) box.getSelectedItem();
        families.stream().filter(f -> f.name().equals(name)).findFirst().ifPresent(f -> {
            addRow(f);
            save();
            refresh();
        });
    }

    private void save() {
        prefs.setCompared(shown.stream().map(r -> r.family.name()).toList());
    }

    private void updateAll() {
        for (Row r : shown) r.update();
        refresh();
    }

    private void refresh() {
        add.setEnabled(shown.size() < FontViewerPreferences.COMPARE_MAX);
        rows.revalidate();
        rows.repaint();
    }

    // ---- for tests

    public List<String> comparedNames() {
        return shown.stream().map(r -> r.family.name()).toList();
    }

    public JTextField textField() {
        return text;
    }

    public JSlider sizeSlider() {
        return size;
    }

    /** The font a row shows, for tests. */
    public Font rowFont(int index) {
        return shown.get(index).sample.getFont();
    }

    public void removeAt(int index) {
        removeRow(shown.get(index));
    }
}
