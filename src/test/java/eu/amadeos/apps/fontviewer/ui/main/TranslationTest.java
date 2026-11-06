package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.bootstrap.FontViewerModule;
import eu.amadeos.apps.fontviewer.domain.Category;
import eu.amadeos.apps.fontviewer.domain.Outlines;
import eu.amadeos.apps.fontviewer.domain.SampleText;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(FontViewerModule.class, FontViewerModule.MESSAGES, Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(FontViewerModule.class, FontViewerModule.MESSAGES));
    }

    @Test
    void everyKeyTheCodeBuildsExists() {
        List<String> keys = new ArrayList<>();
        for (Category c : Category.values()) keys.add(c.textKey());
        for (Filter f : Filter.values()) keys.add(f.textKey());
        for (SampleText s : SampleText.values()) keys.add(s.textKey());
        for (Outlines o : Outlines.values()) keys.add(o.textKey());
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (String key : keys) assertTrue(texts.has(key), language + " " + key);
        }
    }

    /** No two entries share a mnemonic letter in the menu bar or in one menu. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = Map.of(
                "bar", List.of("menu.file", "menu.edit", "menu.view", "menu.font", "menu.options", "menu.help"),
                "file", List.of("menu.file.open", "menu.file.install", "menu.file.more", "menu.file.exit"),
                "edit", List.of("menu.edit.copy", "menu.edit.find"),
                "view", List.of("menu.view.preview", "menu.view.characters", "menu.view.details",
                        "menu.view.compare", "menu.view.reload"),
                "font", List.of("menu.font.compare", "menu.font.folder", "menu.font.remove"),
                "help", List.of("menu.help.handbook", "menu.help.about"),
                "window", List.of("button.install", "button.more", "button.compare", "button.remove",
                        "menu.file", "menu.edit", "menu.view", "menu.font", "menu.options", "menu.help"),
                "characters", List.of("characters.copy", "characters.copy-code"),
                "file-window", List.of("file.install", "file.install-all", "file.show"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertNull(before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void choiceFormatsWork() {
        Messages de = texts(Language.GERMAN);
        assertEquals("Diese Schrift installieren?", de.format("install.question", 1));
        assertEquals("Diese 3 Schriften installieren?", de.format("install.question", 3));
        Messages en = texts(Language.ENGLISH);
        assertTrue(en.format("install.done", 2).startsWith("2 fonts are installed"));
        assertFalse(en.format("remove.confirm", "X", 2).contains("{"));
        assertEquals("Franz jagt im komplett verwahrlosten Taxi quer durch Bayern.", de.get("sample.sentence.text"));
        assertTrue(Arrays.stream(Language.values()).allMatch(l -> !texts(l).get("window.title").isBlank()));
    }
}
