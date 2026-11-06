package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.List;
import java.util.Optional;

/** "Install 3 fonts: For Me / For All Users / Cancel". */
public final class InstallDialog {
    private InstallDialog() {
    }

    public static Optional<InstallTarget> ask(Component parent, Messages msg, List<String> fileNames) {
        String names = fileNames.size() <= 6 ? String.join("\n", fileNames)
                : String.join("\n", fileNames.subList(0, 5)) + "\n" + msg.format("install.and-more", fileNames.size() - 5);
        String text = msg.format("install.question", fileNames.size()) + "\n\n" + names + "\n\n" + msg.get("install.hint");
        Object[] options = {Mnemonics.strip(msg.get("install.for-me")), Mnemonics.strip(msg.get("install.for-all")),
                Mnemonics.strip(msg.get("button.cancel"))};
        int choice = JOptionPane.showOptionDialog(parent, text, msg.get("install.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return switch (choice) {
            case 0 -> Optional.of(InstallTarget.FOR_ME);
            case 1 -> Optional.of(InstallTarget.FOR_ALL);
            default -> Optional.empty();
        };
    }
}
