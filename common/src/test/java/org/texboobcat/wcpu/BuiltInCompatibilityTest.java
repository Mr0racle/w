package org.texboobcat.wcpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuiltInCompatibilityTest {

    @Test
    void noRulesWithoutTheMods() {
        assertTrue(BuiltInCompatibility.mainThreadEntities(id -> false).isEmpty());
    }

    @Test
    void rulesFollowInstalledMods() {
        assertEquals(List.of("createbigcannons:*"),
            BuiltInCompatibility.mainThreadEntities("createbigcannons"::equals));
        assertEquals(List.of("createbigcannons:*", "#ritchiesprojectilelib:precise_motion"),
            BuiltInCompatibility.mainThreadEntities(id -> true));
    }

    @Test
    void blockEntityRulesFollowInstalledMods() {
        assertTrue(BuiltInCompatibility.mainThreadBlockEntities(id -> false).isEmpty());
        assertEquals(List.of("simulated:*"),
            BuiltInCompatibility.mainThreadBlockEntities("simulated"::equals));
    }
}
