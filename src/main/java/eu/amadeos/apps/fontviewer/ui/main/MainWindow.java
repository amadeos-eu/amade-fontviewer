package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.bootstrap.AmadeFontViewerApplication;
import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.FittingSample;
import eu.amadeos.apps.fontviewer.domain.SampleText;
import eu.amadeos.apps.fontviewer.repository.FontViewerPreferences;
import eu.amadeos.apps.fontviewer.service.FontLoader;
import eu.amadeos.apps.fontviewer.ui.compare.CompareWindow;
import eu.amadeos.apps.fontviewer.ui.parts.CharactersPanel;
import eu.amadeos.apps.fontviewer.ui.parts.Detail;
import eu.amadeos.apps.fontviewer.ui.parts.DetailsPanel;
import eu.amadeos.apps.fontviewer.ui.parts.FontChooser;
import eu.amadeos.apps.fontviewer.ui.parts.Waterfall;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The main window: the font list on the left (search, filter, install buttons), on the right the chosen family
 * with Preview, Characters and Details, and the status bar. Font files dropped on the window are installed.
 */
public final class MainWindow extends JFrame implements MainView {
    /** A value with its text, for combo boxes. */
    record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Messages msg;
    private final LocaleSettings locales;
    private final FontLoader loader;
    private final FontViewerPreferences prefs;
    private final JTextField search = new JTextField();
    private final JComboBox<Choice<Filter>> filter = new JComboBox<>();
    private final DefaultListModel<FontFamily> listModel = new DefaultListModel<>();
    private final JList<FontFamily> list = new JList<>(listModel);
    private final JLabel name = new JLabel(" ");
    private final JLabel subtitle = new JLabel(" ");
    private final JButton compareButton = new JButton();
    private final JButton removeButton = new JButton();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField sampleField = new JTextField();
    private final JComboBox<String> styles = new JComboBox<>();
    private final JComboBox<Choice<SampleText>> samples = new JComboBox<>();
    private final Waterfall waterfall = new Waterfall();
    private final DetailsPanel details = new DetailsPanel();
    private final JLabel status = new JLabel(" ");
    private CharactersPanel characters;
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private CompareWindow compare;
    private FamilyModel current;
    private Font font;
    private String sampleText = "";
    private SampleText sampleKind = SampleText.SENTENCE;
    /** True while the window itself changes its controls, so that their listeners stay quiet. */
    private boolean updating;
    private boolean typing;

    public MainWindow(Messages msg, LocaleSettings locales, FontLoader loader, FontViewerPreferences prefs) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        this.loader = loader;
        this.prefs = prefs;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-fontviewer.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        characters = new CharactersPanel(msg);
        menuBar = new MainMenuBar(presenter, msg, locales, presenter::setPage, characters::copyCharacter,
                this::focusSearch);
        setJMenuBar(menuBar);

        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        body.add(listSide(), BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.add(header(), BorderLayout.NORTH);
        tabs.setName("tabs");
        tabs.addTab(Mnemonics.strip(msg.get("tab.preview")), previewPage());
        tabs.addTab(Mnemonics.strip(msg.get("tab.characters")), characters);
        JScrollPane detailsScroll = new JScrollPane(details);
        detailsScroll.setBorder(BorderFactory.createEmptyBorder());
        tabs.addTab(Mnemonics.strip(msg.get("tab.details")), detailsScroll);
        tabs.addChangeListener(e -> {
            if (!updating) presenter.setPage(tabs.getSelectedIndex());
        });
        right.add(tabs, BorderLayout.CENTER);
        body.add(right, BorderLayout.CENTER);

        status.setName("status");
        status.setBorder(BorderFactory.createEmptyBorder(3, 12, 5, 12));
        add(body, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        setTransferHandler(new DropHandler());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        setSize(Math.round(1060 * AmadeUI.fontScale()), Math.round(680 * AmadeUI.fontScale()));
        setMinimumSize(new Dimension(760, 480));
        setLocationRelativeTo(null);
    }

    private JComponent listSide() {
        JPanel side = new JPanel(new BorderLayout(0, 6));
        search.setName("search");
        search.setToolTipText(msg.get("list.search.tip"));
        search.getDocument().addDocumentListener(changes(() -> presenter.setQuery(search.getText())));
        filter.setName("filter");
        for (Filter f : Filter.values()) filter.addItem(new Choice<>(f, msg.get(f.textKey())));
        filter.addActionListener(e -> {
            @SuppressWarnings("unchecked")
            Choice<Filter> c = (Choice<Filter>) filter.getSelectedItem();
            if (c != null) presenter.setFilter(c.value());
        });
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 6));
        top.add(search);
        top.add(filter);
        side.add(top, BorderLayout.NORTH);

        list.setName("families");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new FamilyCellRenderer(msg, f -> loader.cached(f.regular())));
        list.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                presenter.select(list.getSelectedValue());
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(Math.round(260 * AmadeUI.fontScale()), 400));
        side.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.add(button("button.install", presenter::installFonts));
        buttons.add(button("button.more", presenter::getMoreFonts));
        side.add(buttons, BorderLayout.SOUTH);
        return side;
    }

    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout(10, 0));
        name.setName("familyName");
        subtitle.setName("subtitle");
        AmadeUI.style(subtitle, (l, t) -> l.setForeground(t.textInactive()));
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.add(name, BorderLayout.CENTER);
        text.add(subtitle, BorderLayout.SOUTH);
        h.add(text, BorderLayout.CENTER);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        Mnemonics.apply(compareButton, msg.get("button.compare"));
        compareButton.setName("compareButton");
        compareButton.addActionListener(e -> presenter.addToCompare());
        Mnemonics.apply(removeButton, msg.get("button.remove"));
        removeButton.setName("removeButton");
        removeButton.addActionListener(e -> presenter.removeFamily());
        b.add(compareButton);
        b.add(removeButton);
        h.add(b, BorderLayout.EAST);
        return h;
    }

    private JComponent previewPage() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        sampleField.setName("sampleText");
        sampleField.setToolTipText(msg.get("sample.tip"));
        sampleField.getDocument().addDocumentListener(changes(() -> {
            if (updating) return;
            typing = true;
            try {
                presenter.setOwnText(sampleField.getText());
            } finally {
                typing = false;
            }
        }));
        bar.add(sampleField, BorderLayout.CENTER);
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        styles.setName("styles");
        styles.addActionListener(e -> {
            if (!updating && styles.getSelectedItem() != null) presenter.selectStyle((String) styles.getSelectedItem());
        });
        samples.setName("samples");
        for (SampleText kind : SampleText.values()) samples.addItem(new Choice<>(kind, msg.get(kind.textKey())));
        samples.addActionListener(e -> {
            @SuppressWarnings("unchecked")
            Choice<SampleText> c = (Choice<SampleText>) samples.getSelectedItem();
            if (!updating && c != null) presenter.setSample(c.value());
        });
        opts.add(styles);
        opts.add(samples);
        bar.add(opts, BorderLayout.EAST);
        p.add(bar, BorderLayout.NORTH);
        p.add(new JScrollPane(waterfall), BorderLayout.CENTER);
        return p;
    }

    private JButton button(String key, Runnable action) {
        JButton b = Mnemonics.apply(new JButton(), msg.get(key));
        b.setName(key);
        b.addActionListener(e -> action.run());
        return b;
    }

    private DocumentListener changes(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!updating) action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!updating) action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if (!updating) action.run();
            }
        };
    }

    private void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showLoading() {
        updating = true;
        listModel.clear();
        updating = false;
    }

    @Override
    public void showFamilies(List<FontFamily> families, Optional<FontFamily> selected) {
        updating = true;
        try {
            listModel.clear();
            listModel.addAll(families);
            selected.ifPresent(f -> {
                int i = families.indexOf(f);
                if (i >= 0) {
                    list.setSelectedIndex(i);
                    list.ensureIndexIsVisible(i);
                }
            });
        } finally {
            updating = false;
        }
    }

    @Override
    public void refreshList() {
        list.repaint();
    }

    @Override
    public void showFamily(FamilyModel model) {
        current = model;
        font = model.font().orElse(null);
        name.setText(model.family().name());
        Font ui = javax.swing.UIManager.getFont("Label.font");
        float big = (ui == null ? 13f : ui.getSize2D()) * 2.3f;
        name.setFont(font != null && font.canDisplayUpTo(model.family().name()) < 0 ? font.deriveFont(big)
                : (ui == null ? name.getFont() : ui.deriveFont(big)));
        subtitle.setText(model.subtitle());
        removeButton.setEnabled(model.removable());
        removeButton.setToolTipText(model.removable() ? null : model.removeHint());
        compareButton.setEnabled(true);
        updating = true;
        try {
            styles.removeAllItems();
            for (FontFace f : model.family().faces()) styles.addItem(f.style());
            styles.setSelectedItem(model.face().style());
            styles.setEnabled(model.family().faces().size() > 1);
        } finally {
            updating = false;
        }
        menuBar.item("menu.font.remove").setEnabled(model.removable());
        showWaterfall();
    }

    private void showWaterfall() {
        // The ready-made samples are Latin; a font without Latin letters shows its own characters instead.
        // The user's own text is shown as typed, so that missing characters can be seen.
        String shown = sampleKind == SampleText.OWN ? sampleText : FittingSample.forFont(font, sampleText);
        String note = font == null ? msg.get("preview.cannot")
                : shown.equals(sampleText) ? "" : msg.get("preview.own-characters");
        waterfall.show(font, shown, note);
    }

    @Override
    public void showNoFamily() {
        current = null;
        font = null;
        name.setText(msg.get("list.empty"));
        subtitle.setText(" ");
        removeButton.setEnabled(false);
        compareButton.setEnabled(false);
        waterfall.show(null, "", "");
        details.show(List.of());
        characters.show("", null, Coverage.EMPTY);
    }

    @Override
    public void showSample(SampleText kind, String text) {
        sampleText = text;
        sampleKind = kind;
        updating = true;
        try {
            // While the user types, the field already holds the text; changing it now is not allowed.
            if (!typing && !sampleField.getText().equals(text)) sampleField.setText(text);
            for (int i = 0; i < samples.getItemCount(); i++) {
                if (samples.getItemAt(i).value() == kind) samples.setSelectedIndex(i);
            }
        } finally {
            updating = false;
        }
        showWaterfall();
    }

    @Override
    public void showInspection(String family, Optional<Font> shown, Coverage coverage, List<Detail> rows) {
        characters.show(family, shown.orElse(null), coverage);
        details.show(rows);
    }

    @Override
    public void showPage(int page) {
        updating = true;
        try {
            if (page >= 0 && page < tabs.getTabCount()) tabs.setSelectedIndex(page);
        } finally {
            updating = false;
        }
    }

    @Override
    public void showStatus(String text) {
        status.setText(text == null || text.isEmpty() ? " " : text);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        showStatus(message);
    }

    @Override
    public Optional<InstallTarget> askInstallTarget(List<String> fileNames) {
        return InstallDialog.ask(this, msg, fileNames);
    }

    @Override
    public boolean confirm(String message, String yesLabel) {
        Object[] options = {yesLabel, Mnemonics.strip(msg.get("button.cancel"))};
        return JOptionPane.showOptionDialog(this, message, msg.get("window.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    @Override
    public List<Path> chooseFontFiles(boolean multiple) {
        return FontChooser.choose(this, msg, multiple);
    }

    @Override
    public void openCompare(List<FontFamily> families, List<String> compared) {
        if (compare == null || !compare.isDisplayable()) compare = new CompareWindow(msg, loader, prefs);
        compare.show(families, compared, sampleText);
        compare.setVisible(true);
        compare.toFront();
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-fontviewer " + AmadeFontViewerApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        if (compare != null) compare.dispose();
        dispose();
    }

    /** Installs font files dropped on the window. */
    private final class DropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths = files.stream().map(File::toPath).toList();
                // After the drop has finished, so that the source program is not kept waiting by the dialog.
                javax.swing.SwingUtilities.invokeLater(() -> presenter.install(paths));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ---------------------------------------------------------------- for tests and screenshots

    public JList<FontFamily> familyList() {
        return list;
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    public CharactersPanel characters() {
        return characters;
    }

    public DetailsPanel detailsPanel() {
        return details;
    }

    public Waterfall waterfall() {
        return waterfall;
    }

    public MainMenuBar menus() {
        return menuBar;
    }

    public String statusText() {
        return status.getText();
    }

    public String familyName() {
        return name.getText();
    }

    public String subtitleText() {
        return subtitle.getText();
    }

    public JButton removeButton() {
        return removeButton;
    }

    public JComboBox<String> styleBox() {
        return styles;
    }

    public JTextField searchField() {
        return search;
    }

    public JTextField sampleField() {
        return sampleField;
    }

    public CompareWindow compareWindow() {
        return compare;
    }

    Optional<FamilyModel> currentModel() {
        return Optional.ofNullable(current);
    }
}
