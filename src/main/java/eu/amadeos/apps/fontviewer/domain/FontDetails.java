package eu.amadeos.apps.fontviewer.domain;

/**
 * What a font file says about itself in its {@code name}, {@code OS/2} and {@code maxp} tables. Texts the file
 * does not have are empty strings.
 *
 * @param panoseFamily bFamilyType of the PANOSE classification, 0 when the file has none
 * @param panoseSerif  bSerifStyle of the PANOSE classification, 0 when the file has none
 * @param kerning      the file has a {@code kern} or {@code GPOS} table
 * @param variable     the file is a variable font ({@code fvar} table); Java shows its default instance
 * @param family       the typographic family name (name ID 16, else 1)
 * @param style        the typographic style name (name ID 17, else 2)
 */
public record FontDetails(String fullName, String version, String designer, String manufacturer,
                          String license, String licenseUrl, String copyright, String url, String description,
                          int glyphs, Outlines outlines, boolean kerning, boolean variable,
                          int panoseFamily, int panoseSerif, String family, String style) {
    public static final FontDetails NONE = new FontDetails("", "", "", "", "", "", "", "", "", 0,
            Outlines.UNKNOWN, false, false, 0, 0, "", "");

    public FontDetails {
        fullName = clean(fullName);
        version = clean(version);
        designer = clean(designer);
        manufacturer = clean(manufacturer);
        license = clean(license);
        licenseUrl = clean(licenseUrl);
        copyright = clean(copyright);
        url = clean(url);
        description = clean(description);
        family = clean(family);
        style = clean(style);
        if (outlines == null) outlines = Outlines.UNKNOWN;
    }

    /** "Version 1.001; ttfautohint" -> "1.001". */
    public String shortVersion() {
        String v = version.replaceFirst("(?i)^version\\s*", "");
        int cut = v.indexOf(';');
        return (cut >= 0 ? v.substring(0, cut) : v).strip();
    }

    /** A short name for well-known licences, otherwise the start of the licence text. */
    public String shortLicense() {
        String text = (license + " " + licenseUrl).toLowerCase(java.util.Locale.ROOT);
        if (text.contains("open font license") || text.contains("scripts.sil.org/ofl") || text.contains("openfontlicense")) {
            return "SIL Open Font License 1.1";
        }
        if (text.contains("apache license") || text.contains("apache.org/licenses")) return "Apache License 2.0";
        if (text.contains("ubuntu font licen")) return "Ubuntu Font Licence 1.0";
        if (text.contains("gnu general public license") || text.contains("gnu.org/licenses/gpl")) return "GNU GPL";
        if (text.contains("bitstream vera") || text.contains("dejavu")) return "Bitstream Vera licence (free)";
        if (text.contains("public domain")) return "Public domain";
        if (license.isEmpty()) return "";
        String first = license.lines().findFirst().orElse("");
        return first.length() > 90 ? first.substring(0, 87) + "..." : first;
    }

    /** True when the licence is a well-known free one, which allows use in any document. */
    public boolean freeLicense() {
        String s = shortLicense();
        return s.startsWith("SIL") || s.startsWith("Apache") || s.startsWith("Ubuntu") || s.startsWith("GNU")
                || s.startsWith("Bitstream") || s.startsWith("Public");
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\u0000', ' ').strip();
    }
}
