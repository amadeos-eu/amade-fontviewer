package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.commons.process.CommandRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The Debian package a system font came with ({@code dpkg -S}), for "comes with the package ...". */
public class PackageOwner {
    private final CommandRunner runner;
    private final Map<Path, Optional<String>> cache = new ConcurrentHashMap<>();

    public PackageOwner(CommandRunner runner) {
        this.runner = runner;
    }

    public Optional<String> packageOf(Path file) {
        return cache.computeIfAbsent(file, f -> ask(f));
    }

    private Optional<String> ask(Path file) {
        try {
            return parse(runner.output(List.of("dpkg", "-S", file.toString()), Duration.ofSeconds(10)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** "fonts-inter: /usr/share/fonts/opentype/inter/Inter-Regular.otf" -> "fonts-inter". */
    static Optional<String> parse(String output) {
        String line = output.lines().findFirst().orElse("");
        int colon = line.indexOf(": ");
        if (colon <= 0) return Optional.empty();
        String name = line.substring(0, colon);
        int arch = name.indexOf(':');
        if (arch > 0) name = name.substring(0, arch);
        return name.contains(",") || name.contains(" ") ? Optional.empty() : Optional.of(name);
    }
}
