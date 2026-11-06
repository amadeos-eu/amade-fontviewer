package eu.amadeos.apps.fontviewer.service;

import eu.amadeos.apps.fontviewer.TestFonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs packaging/amade-fontviewer-helper with a test folder in place of /usr/local/share/fonts/amadeos. */
class HelperScriptTest {
    @TempDir
    Path dir;

    record Result(int exit, String output) {
    }

    private Result helper(byte[] input, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of("sh", Path.of("packaging/amade-fontviewer-helper")
                .toAbsolutePath().toString()));
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().put("AMADE_FONTVIEWER_FONTS", dir.resolve("fonts/amadeos").toString());
        Process p = pb.start();
        try (var in = p.getOutputStream()) {
            if (input != null) in.write(input);
        } catch (java.io.IOException e) {
            // the helper did not read
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        return new Result(p.exitValue(), out);
    }

    @Test
    void installsAndRemoves() throws Exception {
        byte[] font = Files.readAllBytes(TestFonts.caladea(dir.resolve("src")));
        Result r = helper(font, "install", "Caladea-Regular.ttf");
        assertEquals(0, r.exit(), r.output());
        assertTrue(r.output().contains("@@done"));
        Path installed = dir.resolve("fonts/amadeos/Caladea-Regular.ttf");
        assertArrayEquals(font, Files.readAllBytes(installed));
        assertEquals("rw-r--r--", java.nio.file.attribute.PosixFilePermissions.toString(
                Files.getPosixFilePermissions(installed)));
        r = helper(null, "remove", "Caladea-Regular.ttf");
        assertEquals(0, r.exit(), r.output());
        assertFalse(Files.exists(installed));
    }

    @Test
    void refusesWhatIsNoFont() throws Exception {
        Result r = helper("root:x:0:0:root:/root:/bin/sh\n".getBytes(StandardCharsets.UTF_8), "install", "x.ttf");
        assertEquals(1, r.exit());
        assertTrue(r.output().contains("@@error format"), r.output());
        try (var files = Files.list(dir.resolve("fonts/amadeos"))) {
            assertEquals(0, files.count(), "nothing is left behind");
        }
    }

    @Test
    void refusesBadNames() throws Exception {
        for (String name : List.of("../evil.ttf", "a/b.ttf", ".hidden.ttf", "x.sh", "x", "x;rm.ttf")) {
            Result r = helper(new byte[]{0, 1, 0, 0}, "install", name);
            assertEquals(1, r.exit(), name);
            assertTrue(r.output().contains("@@error name"), name + ": " + r.output());
        }
        Result r = helper(null, "remove", "missing.ttf");
        assertTrue(r.output().contains("@@error missing"));
        assertEquals(1, helper(null, "chmod", "x.ttf").exit());
        assertEquals(1, helper(null, "install").exit());
    }
}
