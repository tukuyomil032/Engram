package com.tukuyomil032.engram.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BattleSessionRegistryTest {

    @Test
    void startGetAndRemoveSession() {
        BattleSessionRegistry registry = new BattleSessionRegistry();
        UUID worldUid = UUID.randomUUID();

        BattleSession created = registry.startSession(worldUid, "ANTI_MELEE", 1000L, 10);
        BattleSession loaded = registry.getSession(worldUid);

        assertEquals(created, loaded);
        assertEquals(1, registry.allSessions().size());

        registry.removeSession(worldUid);
        assertNull(registry.getSession(worldUid));
        assertEquals(0, registry.allSessions().size());
    }
}
