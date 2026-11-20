package org.texboobcat.wcpu;

import java.util.List;

// Lets the /wcpu config command read and change the loader's config file without the common module
// knowing which loader it runs on. The loader installs an implementation at startup.
public interface ConfigBridge {

    /** One setting: its dotted path, description, current and default value, and allowed range. */
    record Entry(String path, String description, String value, String defaultValue,
                 String range, boolean needsRestart) {
    }

    List<Entry> entries();

    /** Sets and saves a setting from text. Returns null on success, otherwise the reason it failed. */
    String set(String path, String raw);

    /** Restores the default. Returns null on success, otherwise the reason it failed. */
    String reset(String path);

    static void install(ConfigBridge bridge) {
        Holder.instance = bridge;
    }

    /** The installed bridge, or null on a loader that has none. */
    static ConfigBridge installed() {
        return Holder.instance;
    }

    final class Holder {
        private static volatile ConfigBridge instance;

        private Holder() {
        }
    }
}
