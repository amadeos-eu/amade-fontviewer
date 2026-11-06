package eu.amadeos.apps.fontviewer.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A font family with its styles, sorted upright before italic, then by weight.
 */
public record FontFamily(String name, List<FontFace> faces, Category category) {
    public FontFamily {
        Objects.requireNonNull(name, "name");
        if (faces == null || faces.isEmpty()) throw new IllegalArgumentException("a family needs a face");
        faces = faces.stream().sorted(Comparator.comparingInt(FontFace::slant)
                .thenComparingInt(FontFace::weight).thenComparing(FontFace::style)).toList();
        Objects.requireNonNull(category, "category");
    }

    /** The upright style nearest to regular, the one the list and the preview show first. */
    public FontFace regular() {
        return faces.stream().min(Comparator.comparingInt(FontFace::distanceFromRegular)).orElseThrow();
    }

    /** The most personal place one of its styles is in: a family with one style of the user's own is the user's. */
    public FontScope scope() {
        return faces.stream().map(FontFace::scope).min(Comparator.naturalOrder()).orElseThrow();
    }

    /** True when one of its styles is the user's own, so that the user may remove it. */
    public boolean mine() {
        return scope() == FontScope.USER;
    }

    public FontFamily withCategory(Category newCategory) {
        return new FontFamily(name, faces, newCategory);
    }

    /** The style with this name, or the regular one. */
    public FontFace face(String style) {
        return faces.stream().filter(f -> f.style().equals(style)).findFirst().orElseGet(this::regular);
    }
}
