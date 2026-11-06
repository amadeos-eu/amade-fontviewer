package eu.amadeos.apps.fontviewer.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.fontviewer.domain.SampleText;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * What the Font Viewer remembers, in {@code ~/.config/amadeos/fontviewer/fontviewer.properties}: the sample
 * text, the family shown last, the open page and the families of the comparison. Written at each change.
 */
public class FontViewerPreferences {
    public static final String FILE = "fontviewer.properties";
    /** The comparison holds this many families at most. */
    public static final int COMPARE_MAX = 6;

    private final Path file;
    private final Properties properties;

    @Inject
    public FontViewerPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public FontViewerPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public SampleText sample() {
        try {
            return SampleText.valueOf(properties.getProperty("sample", SampleText.SENTENCE.name()));
        } catch (IllegalArgumentException e) {
            return SampleText.SENTENCE;
        }
    }

    public void setSample(SampleText sample) {
        set("sample", sample.name());
    }

    /** The text the user typed for {@link SampleText#OWN}. */
    public String ownText() {
        return properties.getProperty("sample.own", "");
    }

    public void setOwnText(String text) {
        set("sample.own", text.length() > 2000 ? text.substring(0, 2000) : text);
    }

    public Optional<String> lastFamily() {
        return Optional.ofNullable(properties.getProperty("family.last")).filter(s -> !s.isBlank());
    }

    public void setLastFamily(String family) {
        set("family.last", family);
    }

    /** 0 preview, 1 characters, 2 details. */
    public int page() {
        try {
            return Math.max(0, Math.min(2, Integer.parseInt(properties.getProperty("page", "0"))));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setPage(int page) {
        set("page", Integer.toString(page));
    }

    public List<String> compared() {
        String value = properties.getProperty("compare", "");
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\|")).map(String::strip).filter(s -> !s.isEmpty()).distinct()
                .limit(COMPARE_MAX).toList();
    }

    public void setCompared(List<String> families) {
        set("compare", String.join("|", families.stream().limit(COMPARE_MAX).toList()));
    }

    public int compareSize() {
        try {
            return Math.max(8, Math.min(144, Integer.parseInt(properties.getProperty("compare.size", "28"))));
        } catch (NumberFormatException e) {
            return 28;
        }
    }

    public void setCompareSize(int size) {
        set("compare.size", Integer.toString(size));
    }

    private void set(String key, String value) {
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-fontviewer");
    }
}
