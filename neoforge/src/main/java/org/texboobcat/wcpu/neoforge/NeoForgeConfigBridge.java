package org.texboobcat.wcpu.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.texboobcat.wcpu.ConfigBridge;

import java.util.ArrayList;
import java.util.List;

// Backs /wcpu config with the NeoForge config spec: descriptions, defaults and limits come from the
// spec itself, so the command always matches the file.
final class NeoForgeConfigBridge implements ConfigBridge {

    @Override
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (ModConfigSpec.ConfigValue<?> value : WcpuNeoForgeConfig.allValues()) {
            String path = String.join(".", value.getPath());
            if (hidden(path)) {
                continue;
            }
            ModConfigSpec.ValueSpec spec = spec(value);
            Object range = spec.getRange();
            entries.add(new Entry(path, spec.getComment() == null ? "" : spec.getComment(),
                String.valueOf(value.get()), String.valueOf(spec.getDefault()),
                range == null ? "" : String.valueOf(range), spec.needsWorldRestart()));
        }
        return entries;
    }

    @Override
    public String set(String path, String raw) {
        ModConfigSpec.ConfigValue<?> value = find(path);
        if (value == null) {
            return "Unknown setting: " + path + " (see /wcpu config)";
        }
        ModConfigSpec.ValueSpec spec = spec(value);
        Object parsed;
        try {
            parsed = parse(spec.getDefault(), raw.strip());
        } catch (IllegalArgumentException failure) {
            return path + ": " + failure.getMessage();
        }
        if (!spec.test(parsed)) {
            Object range = spec.getRange();
            return path + ": value not allowed" + (range == null ? "" : " (range " + range + ")");
        }
        write(value, parsed);
        return null;
    }

    @Override
    public String reset(String path) {
        ModConfigSpec.ConfigValue<?> value = find(path);
        if (value == null) {
            return "Unknown setting: " + path + " (see /wcpu config)";
        }
        write(value, spec(value).getDefault());
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void write(ModConfigSpec.ConfigValue<?> value, Object parsed) {
        ((ModConfigSpec.ConfigValue<Object>) value).set(parsed);
        value.save();
        WcpuNeoForgeConfig.apply();
    }

    private static ModConfigSpec.ConfigValue<?> find(String path) {
        for (ModConfigSpec.ConfigValue<?> value : WcpuNeoForgeConfig.allValues()) {
            if (!hidden(path) && String.join(".", value.getPath()).equals(path)) {
                return value;
            }
        }
        return null;
    }

    private static ModConfigSpec.ValueSpec spec(ModConfigSpec.ConfigValue<?> value) {
        return (ModConfigSpec.ValueSpec) WcpuNeoForgeConfig.SPEC.getSpec().get(value.getPath());
    }

    // Network settings are kept out of the menu: this build never contacts a server.
    private static boolean hidden(String path) {
        return path.endsWith("Endpoint") || path.endsWith("ApiKey");
    }

    private static Object parse(Object sample, String raw) {
        try {
            if (sample instanceof Boolean) {
                return parseBoolean(raw);
            } else if (sample instanceof Integer) {
                return Integer.valueOf(raw);
            } else if (sample instanceof Long) {
                return Long.valueOf(raw);
            } else if (sample instanceof Double) {
                return Double.valueOf(raw.replace(',', '.'));
            } else if (sample instanceof List) {
                return parseList(raw);
            }
            return raw;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("expected a number, got '" + raw + "'", failure);
        }
    }

    private static Boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            return Boolean.valueOf(raw);
        }
        throw new IllegalArgumentException("expected true or false, got '" + raw + "'");
    }

    private static List<String> parseList(String raw) {
        List<String> items = new ArrayList<>();
        for (String item : raw.replace("[", "").replace("]", "").split(",")) {
            String cleaned = item.strip().replace("\"", "");
            if (!cleaned.isEmpty()) {
                items.add(cleaned);
            }
        }
        return items;
    }
}
