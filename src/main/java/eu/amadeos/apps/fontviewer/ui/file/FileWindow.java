package eu.amadeos.apps.fontviewer.ui.file;

import eu.amadeos.apps.fontviewer.ui.parts.DetailsPanel;
import eu.amadeos.apps.fontviewer.ui.parts.Waterfall;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * A font file opened from Files: its name in its own font, a short preview, its details and the buttons to
 * install it, or, when it is installed already, to show it in the Font Viewer.
 */
public final class FileWindow extends JFrame implements FileView {
    private final Messages msg;
    private final JLabel title = new JLabel(" ");
    private final JLabel state = new JLabel(" ");
    private final JButton forMe = new JButton();
    private final JButton forAll = new JButton();
    private final JButton show = new JButton();
    private final Waterfall waterfall = new Waterfall();
    private final DetailsPanel details = new DetailsPanel();
    private final JLabel hint = new JLabel();
    private FilePresenter presenter;
    private FileModel model;

    public FileWindow(Messages msg) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        setName("fileWindow");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-fontviewer.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
    }

    public void bind(FilePresenter newPresenter) {
        presenter = newPresenter;
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        JPanel head = new JPanel(new BorderLayout(10, 0));
        title.setName("title");
        state.setName("state");
        AmadeUI.style(state, (l, t) -> l.setForeground(t.textInactive()));
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.add(title, BorderLayout.CENTER);
        text.add(state, BorderLayout.SOUTH);
        head.add(text, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        Mnemonics.apply(forMe, msg.get("file.install"));
        forMe.setName("installForMe");
        forMe.addActionListener(e -> presenter.install(false));
        Mnemonics.apply(forAll, msg.get("file.install-all"));
        forAll.setName("installForAll");
        forAll.addActionListener(e -> presenter.install(true));
        Mnemonics.apply(show, msg.get("file.show"));
        show.setName("showInViewer");
        show.addActionListener(e -> presenter.showInViewer());
        buttons.add(forMe);
        buttons.add(forAll);
        buttons.add(show);
        head.add(buttons, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);

        waterfall.setSizes(12, 18, 28, 40);
        JScrollPane preview = new JScrollPane(waterfall);
        preview.setPreferredSize(new Dimension(700, 200));
        JPanel mid = new JPanel(new BorderLayout(0, 10));
        mid.add(preview, BorderLayout.NORTH);
        JScrollPane info = new JScrollPane(details);
        info.setBorder(BorderFactory.createEmptyBorder());
        mid.add(info, BorderLayout.CENTER);
        p.add(mid, BorderLayout.CENTER);

        hint.setText("<html>" + msg.get("file.hint") + "</html>");
        AmadeUI.style(hint, (l, t) -> l.setForeground(t.textInactive()));
        p.add(hint, BorderLayout.SOUTH);
        add(p);
        showBusy(msg.get("status.loading"));
        setSize(Math.round(780 * AmadeUI.fontScale()), Math.round(560 * AmadeUI.fontScale()));
        setMinimumSize(new Dimension(520, 400));
        setLocationByPlatform(true);
    }

    @Override
    public void showFile(FileModel newModel) {
        model = newModel;
        setTitle(msg.format("file.window-title", newModel.file().getFileName()));
        title.setText(newModel.title());
        Font ui = UIManager.getFont("Label.font");
        float big = (ui == null ? 13f : ui.getSize2D()) * 2.4f;
        Font font = newModel.font().orElse(null);
        title.setFont(font != null && font.canDisplayUpTo(newModel.title()) < 0 ? font.deriveFont(big)
                : (ui == null ? title.getFont() : ui.deriveFont(big)));
        state.setText(newModel.stateText());
        boolean isFont = newModel.state() != FileModel.State.NOT_A_FONT;
        boolean installed = newModel.state() == FileModel.State.INSTALLED;
        forMe.setVisible(isFont && !installed);
        forAll.setVisible(isFont && !installed);
        forMe.setEnabled(true);
        forAll.setEnabled(true);
        show.setVisible(installed);
        hint.setVisible(isFont && !installed);
        waterfall.show(font, msg.get("sample.sentence.text"), isFont ? msg.get("preview.cannot") : msg.get("file.not-a-font"));
        details.show(newModel.details());
        getRootPane().setDefaultButton(installed ? show : forMe);
    }

    @Override
    public void showBusy(String text) {
        state.setText(text);
        forMe.setEnabled(false);
        forAll.setEnabled(false);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean confirm(String message, String yesLabel) {
        Object[] options = {yesLabel, Mnemonics.strip(msg.get("button.cancel"))};
        return JOptionPane.showOptionDialog(this, message, getTitle(), JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    @Override
    public void close() {
        dispose();
    }

    // ---- for tests and screenshots

    public String titleText() {
        return title.getText();
    }

    public String stateText() {
        return state.getText();
    }

    public JButton installButton() {
        return forMe;
    }

    public JButton installAllButton() {
        return forAll;
    }

    public JButton showButton() {
        return show;
    }

    public DetailsPanel detailsPanel() {
        return details;
    }

    public java.util.Optional<FileModel> model() {
        return java.util.Optional.ofNullable(model);
    }
}
