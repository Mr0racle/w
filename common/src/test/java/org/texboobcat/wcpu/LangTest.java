package org.texboobcat.wcpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LangTest {

    private static final Pattern SPEC = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdf]");

    @AfterEach
    void restoreDefault() {
        Lang.set(Lang.ENGLISH);
    }

    @Test
    void englishIsShownAsWritten() {
        assertEquals("%d violation(s)".formatted(3), Lang.f("%d violation(s)", 3));
    }

    @Test
    void russianPhrasesAreUsedAndUnknownOnesFallBack() {
        Lang.set(Lang.RUSSIAN);
        assertEquals("нарушений: 3", Lang.f("%d violation(s)", 3));
        assertEquals("not translated", Lang.t("not translated"));
    }

    @Test
    void everyTranslationKeepsTheSameFormatArguments() {
        LangRu.PHRASES.forEach((english, russian) -> assertEquals(specs(english), specs(russian), english));
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        Lang.set("de");
        assertEquals(Lang.ENGLISH, Lang.code());
    }

    private static java.util.List<String> specs(String text) {
        java.util.List<String> found = new java.util.ArrayList<>();
        Matcher matcher = SPEC.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group().replaceAll("\\d+\\.", "."));
        }
        return found;
    }
}
