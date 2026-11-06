package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntConsumer;

/** File, Edit, View, Font, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final Messages msg;
    private final Map<String, JMenuItem> items = new HashMap<>();

    /**
     * @param page          shows a page of the window (0 Preview, 1 Characters, 2 Details)
     * @param copyCharacter copies the selected character of the Characters page
     * @param findFont      puts the focus into the search field of the list
     */
    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales, IntConsumer page,
                       Runnable copyCharacter, Runnable findFont) {
        EdtExecutor.requireEdt();
        this.msg = msg;

        JMenu file = menu("menu.file");
        file.add(item("menu.file.open", key(KeyEvent.VK_O, CTRL), presenter::openFontFile));
        file.add(item("menu.file.install", key(KeyEvent.VK_I, CTRL), presenter::installFonts));
        file.add(item("menu.file.more", null, presenter::getMoreFonts));
        file.addSeparator();
        file.add(item("menu.file.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu edit = menu("menu.edit");
        edit.add(item("menu.edit.copy", key(KeyEvent.VK_C, CTRL | InputEvent.SHIFT_DOWN_MASK), copyCharacter));
        edit.add(item("menu.edit.find", key(KeyEvent.VK_F, CTRL), findFont));

        JMenu view = menu("menu.view");
        view.add(item("menu.view.preview", key(KeyEvent.VK_1, CTRL), () -> page.accept(0)));
        view.add(item("menu.view.characters", key(KeyEvent.VK_2, CTRL), () -> page.accept(1)));
        view.add(item("menu.view.details", key(KeyEvent.VK_3, CTRL), () -> page.accept(2)));
        view.addSeparator();
        view.add(item("menu.view.compare", key(KeyEvent.VK_K, CTRL), presenter::openCompare));
        view.addSeparator();
        view.add(item("menu.view.reload", key(KeyEvent.VK_F5, 0), presenter::reload));

        JMenu font = menu("menu.font");
        font.add(item("menu.font.compare", key(KeyEvent.VK_D, CTRL), presenter::addToCompare));
        font.add(item("menu.font.folder", null, presenter::showInFolder));
        font.addSeparator();
        font.add(item("menu.font.remove", key(KeyEvent.VK_DELETE, 0), presenter::removeFamily));

        if (locales != null) {
            JMenu options = menu("menu.options");
            options.add(languageMenu(presenter, locales));
        }

        JMenu help = menu("menu.help");
        help.add(item("menu.help.handbook", key(KeyEvent.VK_F1, 0), presenter::handbook));
        help.addSeparator();
        help.add(item("menu.help.about", null, presenter::about));
    }

    public JMenuItem item(String name) {
        return items.get(name);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }
}
