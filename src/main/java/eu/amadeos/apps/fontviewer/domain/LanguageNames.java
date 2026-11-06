package eu.amadeos.apps.fontviewer.domain;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** fontconfig language codes ({@code de}, {@code pt}, {@code zh-tw}) as names in the user's language. */
public final class LanguageNames {
    /** Shown first when a font has them, because most amadeOS users write them. */
    private static final List<String> FIRST = List.of("de", "en", "fr", "es", "it", "nl", "pl", "pt", "cs",
            "sv", "da", "fi", "el", "ru", "uk", "tr", "ar", "he", "hi", "zh-cn", "ja", "ko");

    private LanguageNames() {
    }

    /** All names, the usual European languages first, then the others alphabetically; unknown codes dropped. */
    public static List<String> names(Collection<String> codes, Locale locale) {
        Set<String> result = new LinkedHashSet<>();
        for (String code : FIRST) if (codes.contains(code)) name(code, locale).ifPresent(result::add);
        List<String> rest = new ArrayList<>();
        for (String code : codes) {
            if (!FIRST.contains(code)) name(code, locale).filter(n -> !result.contains(n)).ifPresent(rest::add);
        }
        Collator collator = Collator.getInstance(locale);
        rest.stream().distinct().sorted(collator).forEach(result::add);
        return List.copyOf(result);
    }

    static java.util.Optional<String> name(String code, Locale locale) {
        String tag = code.replace('_', '-');
        Locale l = Locale.forLanguageTag(tag);
        String language = l.getDisplayLanguage(locale);
        if (language.isEmpty() || language.equalsIgnoreCase(l.getLanguage())) return java.util.Optional.empty();
        String country = l.getDisplayCountry(locale);
        return java.util.Optional.of(country.isEmpty() ? language : language + " (" + country + ")");
    }
}
