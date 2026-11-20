package org.texboobcat.wcpu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// Compatibility rules that ship inside the mod and switch on by themselves when the affected mod is
// installed, so players do not have to edit config files and nothing depends on a remote server.
//
// Entries use the same syntax as compatibility.mainThreadEntities: an entity id, "mod:*" for every
// entity of a mod, or "#namespace:tag" for an entity type tag.
public final class BuiltInCompatibility {

    // Ritchie's Projectile Library and Create Big Cannons tick their projectiles in a way a region
    // worker cannot do safely: they queue chunk force-loads from the projectile's own tick and fly
    // into chunks that are still loading. Those entities tick on the main thread instead.
    private static final Map<String, List<String>> MAIN_THREAD_ENTITIES = Map.of(
        "ritchiesprojectilelib", List.of("#ritchiesprojectilelib:precise_motion"),
        "createbigcannons", List.of("createbigcannons:*"));

    // Block entities that must tick on the main thread. Simulated's bearings, and the other blocks
    // that assemble a structure, create a sub-level from inside their own tick, which allocates
    // plot chunks and touches shared state a region worker does not own.
    private static final Map<String, List<String>> MAIN_THREAD_BLOCK_ENTITIES = Map.of(
        "simulated", List.of("simulated:*"));

    private BuiltInCompatibility() {
    }

    /** Main-thread entity entries for the mods that are loaded, in a stable order. */
    public static List<String> mainThreadEntities(Predicate<String> modLoaded) {
        List<String> modIds = new ArrayList<>(MAIN_THREAD_ENTITIES.keySet());
        modIds.sort(String::compareTo);
        List<String> entries = new ArrayList<>();
        for (String modId : modIds) {
            if (modLoaded.test(modId)) {
                entries.addAll(MAIN_THREAD_ENTITIES.get(modId));
            }
        }
        return entries;
    }

    /** Main-thread block-entity entries for the mods that are loaded, in a stable order. */
    public static List<String> mainThreadBlockEntities(Predicate<String> modLoaded) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, List<String>> rule : MAIN_THREAD_BLOCK_ENTITIES.entrySet()) {
            if (modLoaded.test(rule.getKey())) {
                entries.addAll(rule.getValue());
            }
        }
        return entries;
    }
}
