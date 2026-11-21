package org.texboobcat.wcpu;

import java.util.Locale;

// Text of the /wcpu commands. The English text is the key: a phrase with no translation (or the
// default language) is shown as written. The language is a server setting (general.language,
// /wcpu language) because the mod may be server-only, so clients cannot supply translations.
public final class Lang {

    public static final String ENGLISH = "en";
    public static final String RUSSIAN = "ru";

    private static volatile String code = ENGLISH;

    private Lang() {
    }

    public static void set(String language) {
        code = RUSSIAN.equalsIgnoreCase(language) ? RUSSIAN : ENGLISH;
    }

    public static String code() {
        return code;
    }

    public static boolean supported(String language) {
        String lower = language.toLowerCase(Locale.ROOT);
        return lower.equals(ENGLISH) || lower.equals(RUSSIAN);
    }

    /** Translates a fixed phrase. */
    public static String t(String english) {
        return RUSSIAN.equals(code) ? LangRu.PHRASES.getOrDefault(english, english) : english;
    }

    /** Translates a format string, then fills it in. */
    public static String f(String english, Object... args) {
        return String.format(Locale.ROOT, t(english), args);
    }

    /** Description of a config setting, by its dotted path. */
    public static String description(String path, String english) {
        return RUSSIAN.equals(code) ? LangRu.DESCRIPTIONS.getOrDefault(path, english) : english;
    }
}
