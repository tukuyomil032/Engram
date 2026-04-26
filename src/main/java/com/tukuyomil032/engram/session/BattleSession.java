package com.tukuyomil032.engram.session;

import com.tukuyomil032.engram.data.BattleRecord;
import com.tukuyomil032.engram.data.PlayerContribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BattleSession {
    private final UUID worldUid;
    private final String strategyUsed;
    private final long startedAt;
    private final int expectedCrystals;
    private final Map<UUID, MutableContribution> contributions = new HashMap<>();
    private final List<CrystalEvent> crystals = new ArrayList<>();

    public BattleSession(UUID worldUid, String strategyUsed, long startedAt, int expectedCrystals) {
        this.worldUid = worldUid;
        this.strategyUsed = strategyUsed;
        this.startedAt = startedAt;
        this.expectedCrystals = expectedCrystals;
    }

    public void recordBowDamage(UUID playerUuid, double damage) {
        contribution(playerUuid).bowDamage += damage;
    }

    public void recordMeleeDamage(UUID playerUuid, double damage) {
        contribution(playerUuid).meleeDamage += damage;
    }

    public void recordExplosionDamage(UUID playerUuid, double damage) {
        contribution(playerUuid).explosionDamage += damage;
    }

    public void recordAltitudeSample(UUID playerUuid, double altitude) {
        MutableContribution contribution = contribution(playerUuid);
        contribution.altitudeSum += altitude;
        contribution.altitudeSamples += 1;
    }

    public void recordCrystalDestroyed(UUID destroyerUuid, long timestampMs, int crystalIndex) {
        contribution(destroyerUuid);
        crystals.add(new CrystalEvent(destroyerUuid, timestampMs, crystalIndex));
    }

    public BattleRecord finalizeBattle(long endedAt, boolean victory) {
        long duration = Math.max(0L, endedAt - startedAt);
        List<PlayerContribution> players = new ArrayList<>();
        for (Map.Entry<UUID, MutableContribution> entry : contributions.entrySet()) {
            MutableContribution value = entry.getValue();
            double avgAltitude = value.altitudeSamples == 0 ? 0.0D : value.altitudeSum / value.altitudeSamples;
            players.add(new PlayerContribution(
                entry.getKey(),
                value.bowDamage,
                value.meleeDamage,
                value.explosionDamage,
                avgAltitude,
                1
            ));
        }

        Long crystalTimeMs = null;
        if (expectedCrystals > 0 && crystals.size() >= expectedCrystals) {
            long latestCrystalTimestamp = crystals.stream()
                .max(Comparator.comparingLong(CrystalEvent::timestampMs))
                .map(CrystalEvent::timestampMs)
                .orElse(startedAt);
            crystalTimeMs = Math.max(0L, latestCrystalTimestamp - startedAt);
        }

        return new BattleRecord(
            worldUid,
            startedAt,
            duration,
            players.size(),
            victory,
            crystalTimeMs,
            strategyUsed,
            players
        );
    }

    public UUID getWorldUid() {
        return worldUid;
    }

    private MutableContribution contribution(UUID playerUuid) {
        return contributions.computeIfAbsent(playerUuid, ignored -> new MutableContribution());
    }

    private static final class MutableContribution {
        private double bowDamage;
        private double meleeDamage;
        private double explosionDamage;
        private double altitudeSum;
        private int altitudeSamples;
    }

    private record CrystalEvent(UUID destroyerUid, long timestampMs, int crystalIndex) {
    }
}
