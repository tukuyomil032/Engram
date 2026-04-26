package com.tukuyomil032.engram.data;

import java.util.List;
import java.util.UUID;

public record BattleRecord(
    UUID worldUid,
    long foughtAt,
    long durationMs,
    int playerCount,
    boolean victory,
    Long crystalTimeMs,
    String strategyUsed,
    List<PlayerContribution> players
) {
}
