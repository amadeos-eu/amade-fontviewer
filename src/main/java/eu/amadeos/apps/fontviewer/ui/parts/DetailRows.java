package eu.amadeos.apps.fontviewer.ui.parts;

import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontDetails;
import eu.amadeos.apps.fontviewer.domain.LanguageNames;
import eu.amadeos.commons.i18n.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Builds the detail lines of a font from what its file says; empty values are left out. */
public final class DetailRows {
    /** How many language names are listed before "and 70 more". */
    static final int LANGUAGES_SHOWN = 6;

    private final Messages msg;
    private final List<Detail> rows = new ArrayList<>();

    public DetailRows(Messages msg) {
        this.msg = msg;
    }

    public DetailRows add(String key, String value) {
        if (value != null && !value.isBlank()) rows.add(new Detail(msg.get(key), value.strip()));
        return this;
    }

    /** Version, designer, licence, characters, languages and format. */
    public DetailRows font(FontDetails d, Coverage coverage, Collection<String> languages) {
        add("details.version", d.shortVersion());
        add("details.designer", d.designer().isBlank() ? d.manufacturer() : d.designer());
        String license = d.shortLicense();
        if (!license.isBlank() && d.freeLicense()) license = msg.format("details.license.free", license);
        add("details.license", license);
        if (!coverage.isEmpty()) {
            add("details.characters", msg.format("details.characters.value", coverage.size(), coverage.blocks().size()));
        }
        add("details.languages", languages(languages));
        add("details.format", format(d));
        return this;
    }

    /** The rest: copyright, description, web address. */
    public DetailRows more(FontDetails d) {
        add("details.description", d.description());
        add("details.copyright", d.copyright());
        add("details.web", d.url());
        return this;
    }

    String languages(Collection<String> codes) {
        List<String> names = LanguageNames.names(codes, msg.locale());
        if (names.isEmpty()) return "";
        if (names.size() <= LANGUAGES_SHOWN + 1) return String.join(", ", names);
        return msg.format("details.languages.more", String.join(", ", names.subList(0, LANGUAGES_SHOWN)),
                names.size() - LANGUAGES_SHOWN);
    }

    String format(FontDetails d) {
        StringBuilder b = new StringBuilder(msg.get(d.outlines().textKey()));
        if (d.kerning()) b.append(", ").append(msg.get("details.format.kerning"));
        if (d.variable()) b.append(", ").append(msg.get("details.format.variable"));
        return b.toString();
    }

    public List<Detail> build() {
        return List.copyOf(rows);
    }
}
