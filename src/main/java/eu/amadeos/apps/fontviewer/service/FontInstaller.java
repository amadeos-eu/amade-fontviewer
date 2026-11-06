package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.domain.FontFace;
import eu.amadeos.apps.fontviewer.domain.FontFiles;
import eu.amadeos.apps.fontviewer.domain.FontScope;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Installs and removes fonts. "For me" copies the files into the user's font folder; "for all users" hands each
 * file on standard input to {@code /usr/libexec/amade-fontviewer-helper} through {@code pkexec}, which writes it
 * to {@code /usr/local/share/fonts/amadeos} (root never opens a path the user named). Afterwards fontconfig's
 * cache is renewed, so that other programs find the fonts when they start.
 */
public class FontInstaller {
    public static final String HELPER = "/usr/libexec/amade-fontviewer-helper";
    /** Replaces the helper command, for trying the program without polkit. */
    public static final String HELPER_VARIABLE = "AMADE_FONTVIEWER_HELPER";
    private static final Logger LOG = LoggerFactory.getLogger(FontInstaller.class);

    private final CommandRunner runner;
    private final FontPlaces places;
    private final List<String> helper;

    public FontInstaller(CommandRunner runner, FontPlaces places) {
        this(runner, places, helperCommand(System.getenv(HELPER_VARIABLE), System.getProperty("user.name")));
    }

    /** With an explicit helper command, e.g. a fake one in tests. */
    public FontInstaller(CommandRunner runner, FontPlaces places, List<String> helper) {
        this.runner = runner;
        this.places = places;
        this.helper = List.copyOf(helper);
    }

    static List<String> helperCommand(String override, String user) {
        if (override != null && !override.isBlank()) return List.of(override.strip().split("\\s+"));
        return "root".equals(user) ? List.of(HELPER) : List.of("pkexec", HELPER);
    }

    /** The name the file gets in a font folder, or a NOT_A_FONT failure. */
    public static String targetName(Path file) throws InstallException {
        if (!Files.isRegularFile(file) || !FontFiles.hasFontExtension(file) || !FontFiles.looksLikeFont(file)) {
            throw new InstallException(InstallException.Reason.NOT_A_FONT, file.getFileName().toString());
        }
        return FontFiles.safeName(file.getFileName().toString()).orElseThrow(
                () -> new InstallException(InstallException.Reason.NOT_A_FONT, file.getFileName().toString()));
    }

    /** The files whose name is already taken in the target folder, so that the window can ask first. */
    public List<Path> existing(Collection<Path> files, boolean forAll) {
        Path dir = forAll ? places.allUsersFonts() : places.userFonts();
        List<Path> taken = new ArrayList<>();
        for (Path f : files) {
            try {
                if (Files.exists(dir.resolve(targetName(f)))) taken.add(f);
            } catch (InstallException e) {
                // not a font: reported when installing
            }
        }
        return taken;
    }

    /** Copies the fonts into the user's font folder; returns the new files. */
    public List<Path> installForMe(Collection<Path> files) throws InstallException {
        Path dir = places.userFonts();
        List<Path> installed = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            for (Path f : files) {
                Path target = dir.resolve(targetName(f));
                if (target.toAbsolutePath().normalize().equals(f.toAbsolutePath().normalize())) {
                    installed.add(target);
                    continue;
                }
                Path part = dir.resolve("." + target.getFileName() + ".part");
                Files.copy(f, part, StandardCopyOption.REPLACE_EXISTING);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                installed.add(target);
            }
        } catch (IOException e) {
            throw new InstallException(InstallException.Reason.FAILED, e.getMessage(), e);
        } finally {
            if (!installed.isEmpty()) refreshCache(dir);
        }
        return installed;
    }

    /** Installs the fonts for every user, through the helper; asks for an administrator's password once. */
    public List<Path> installForAll(Collection<Path> files) throws InstallException {
        List<Path> installed = new ArrayList<>();
        for (Path f : files) {
            String name = targetName(f);
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(f);
            } catch (IOException e) {
                throw new InstallException(InstallException.Reason.FAILED, e.getMessage(), e);
            }
            runHelper(bytes, "install", name);
            installed.add(places.allUsersFonts().resolve(name));
        }
        return installed;
    }

    /**
     * Removes the files of these faces: the user's own go to the wastebasket (or are deleted where there is
     * none), those in the folder for all users through the helper. Package fonts are left alone.
     *
     * @return the files that are gone
     */
    public List<Path> remove(Collection<FontFace> faces) throws InstallException {
        Set<Path> files = new LinkedHashSet<>();
        for (FontFace face : faces) files.add(face.file());
        List<Path> removed = new ArrayList<>();
        boolean user = false;
        for (Path file : files) {
            FontScope scope = places.scopeOf(file);
            if (scope == FontScope.USER) {
                trash(file);
                removed.add(file);
                user = true;
            } else if (places.removableForAll(file)) {
                runHelper(null, "remove", file.getFileName().toString());
                removed.add(file);
            }
        }
        if (user) refreshCache(places.userFonts());
        return removed;
    }

    private void trash(Path file) throws InstallException {
        try {
            if (runner.run(List.of("gio", "trash", file.toString()), Duration.ofSeconds(20)).succeeded()) return;
        } catch (IOException e) {
            LOG.info("No wastebasket for {}: {}", file, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new InstallException(InstallException.Reason.FAILED, e.getMessage(), e);
        }
    }

    /** {@code fc-cache} for the folder; a failure only means other programs see the font a little later. */
    void refreshCache(Path dir) {
        try {
            runner.run(List.of("fc-cache", "-f", dir.toString()), Duration.ofMinutes(2));
        } catch (IOException e) {
            LOG.warn("fc-cache failed: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --8<-- [start:helper]
    private void runHelper(byte[] input, String... arguments) throws InstallException {
        List<String> command = new ArrayList<>(helper);
        command.addAll(List.of(arguments));
        Process process;
        try {
            process = runner.start(command);
        } catch (ProgramMissingException e) {
            throw new InstallException(InstallException.Reason.NOT_AUTHORIZED, e.getMessage(), e);
        } catch (IOException e) {
            throw new InstallException(InstallException.Reason.FAILED, e.getMessage(), e);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> copy(process.getInputStream(), out));
        Thread errors = Thread.ofVirtual().start(() -> copy(process.getErrorStream(), new ByteArrayOutputStream()));
        try (OutputStream stdin = process.getOutputStream()) {
            if (input != null) stdin.write(input);
        } catch (IOException e) {
            // The helper ended early (password dialog closed); its exit code says why.
        }
        try {
            // The password dialog waits for a person, so the limit is generous.
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new InstallException(InstallException.Reason.FAILED, "the helper did not finish");
            }
            reader.join(Duration.ofSeconds(5));
            errors.join(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new InstallException(InstallException.Reason.FAILED, "interrupted");
        }
        String output;
        synchronized (out) {
            output = out.toString(StandardCharsets.UTF_8);
        }
        check(process.exitValue(), output);
    }
    // --8<-- [end:helper]

    /** Turns the helper's exit code and "@@error ID MESSAGE" line into an exception; nothing for success. */
    static void check(int exit, String output) throws InstallException {
        if (exit == 0) return;
        // pkexec's own codes: 126 when the password dialog was closed, 127 when polkit refused.
        if (exit == 126) throw new InstallException(InstallException.Reason.DISMISSED, "dismissed");
        if (exit == 127) throw new InstallException(InstallException.Reason.NOT_AUTHORIZED, "not authorized");
        Optional<String> error = output.lines().filter(l -> l.startsWith("@@error ")).findFirst();
        String message = error.map(l -> l.substring(8)).orElse("exit code " + exit);
        InstallException.Reason reason = message.startsWith("format ")
                ? InstallException.Reason.NOT_A_FONT : InstallException.Reason.FAILED;
        throw new InstallException(reason, message);
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        byte[] buffer = new byte[8192];
        try (in) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                synchronized (out) {
                    out.write(buffer, 0, n);
                }
            }
        } catch (IOException e) {
            // the process ended
        }
    }
}
