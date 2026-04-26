package com.tukuyomil032.engram.data;

import java.util.UUID;

public record PlayerContribution(
    UUID playerUuid,
    double bowDamage,
    double meleeDamage,
    double explosionDamage,
    double avgAltitude,
    int fightCount
) {
}
