package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.domain.Category;
import eu.amadeos.apps.fontviewer.domain.FontFamily;

import java.util.Locale;
import java.util.Optional;

/** The choices of the box above the font list. */
public enum Filter {
    ALL(null), MINE(null), SERIF(Category.SERIF), SANS_SERIF(Category.SANS_SERIF), MONOSPACE(Category.MONOSPACE),
    HANDWRITING(Category.HANDWRITING), DISPLAY(Category.DISPLAY), SYMBOLS(Category.SYMBOLS);

    private final Category category;

    Filter(Category category) {
        this.category = category;
    }

    public String textKey() {
        return "filter." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public Optional<Category> category() {
        return Optional.ofNullable(category);
    }

    public boolean accepts(FontFamily family) {
        if (this == ALL) return true;
        if (this == MINE) return family.mine() || family.scope() == eu.amadeos.apps.fontviewer.domain.FontScope.ALL_USERS;
        return family.category() == category;
    }
}
