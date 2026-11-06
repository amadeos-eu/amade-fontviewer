package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.apps.fontviewer.domain.FontFiles;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** The file chooser for font files; starts in Downloads, where new fonts usually are. */
public final class FontChooser {
    private FontChooser() {
    }

    public static List<Path> choose(Component parent, Messages msg, boolean multiple) {
        Path start = XdgDirectories.system().downloads();
        JFileChooser chooser = new JFileChooser(Files.isDirectory(start) ? start.toFile() : null);
        chooser.setDialogTitle(msg.get(multiple ? "chooser.install" : "chooser.open"));
        chooser.setMultiSelectionEnabled(multiple);
        FileNameExtensionFilter fonts = new FileNameExtensionFilter(msg.get("chooser.filter"),
                FontFiles.EXTENSIONS.toArray(String[]::new));
        chooser.addChoosableFileFilter(fonts);
        chooser.setFileFilter(fonts);
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return List.of();
        File[] files = multiple ? chooser.getSelectedFiles() : new File[]{chooser.getSelectedFile()};
        return Arrays.stream(files).filter(f -> f != null).map(File::toPath).toList();
    }
}
