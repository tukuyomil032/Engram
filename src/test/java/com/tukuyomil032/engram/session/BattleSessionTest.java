package com.tukuyomil032.engram.session;

import com.tukuyomil032.engram.data.BattleRecord;
import com.tukuyomil032.engram.data.PlayerContribution;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BattleSessionTest {

    @Test
    void finalizeBuildsBattleRecordWithAggregatedStats() {
        UUID worldUid = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        long startedAt = 1_000_000L;

        BattleSession session = new BattleSession(worldUid, "ANTI_ARCHER", startedAt, 2);
        session.recordBowDamage(playerA, 100.0);
        session.recordMeleeDamage(playerA, 20.0);
        session.recordExplosionDamage(playerB, 35.0);
        session.recordAltitudeSample(playerA, 60.0);
        session.recordAltitudeSample(playerA, 70.0);
        session.recordAltitudeSample(playerB, 45.0);
        session.recordCrystalDestroyed(playerA, startedAt + 15_000L, 1);
        session.recordCrystalDestroyed(playerB, startedAt + 30_000L, 2);

        BattleRecord battleRecord = session.finalizeBattle(startedAt + 180_000L, true);

        assertEquals(worldUid, battleRecord.worldUid());
        assertEquals("ANTI_ARCHER", battleRecord.strategyUsed());
        assertEquals(180_000L, battleRecord.durationMs());
        assertEquals(2, battleRecord.playerCount());
        assertEquals(30_000L, battleRecord.crystalTimeMs());

        PlayerContribution p1 = findContribution(battleRecord, playerA);
        PlayerContribution p2 = findContribution(battleRecord, playerB);
        assertEquals(100.0, p1.bowDamage());
        assertEquals(20.0, p1.meleeDamage());
        assertEquals(0.0, p1.explosionDamage());
        assertEquals(65.0, p1.avgAltitude());
        assertEquals(0.0, p2.bowDamage());
        assertEquals(0.0, p2.meleeDamage());
        assertEquals(35.0, p2.explosionDamage());
        assertEquals(45.0, p2.avgAltitude());
    }

    @Test
    void crystalTimeIsNullWhenExpectedCrystalsNotReached() {
        UUID worldUid = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        long startedAt = 5_000L;

        BattleSession session = new BattleSession(worldUid, "ANTI_SPEEDRUN", startedAt, 3);
        session.recordCrystalDestroyed(player, startedAt + 10_000L, 1);
        BattleRecord battleRecord = session.finalizeBattle(startedAt + 20_000L, false);

        assertNull(battleRecord.crystalTimeMs());
    }

    private static PlayerContribution findContribution(BattleRecord record, UUID playerUid) {
        return record.players().stream()
            .filter(contribution -> contribution.playerUuid().equals(playerUid))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Contribution not found"));
    }
}
