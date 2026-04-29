package com.tukuyomil032.engram.listener;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageClassifierTest {

    @Test
    void classifiesProjectileAsBow() {
        DamageCategory category = DamageClassifier.classify(EntityDamageEvent.DamageCause.PROJECTILE, "AIR");
        assertEquals(DamageCategory.BOW, category);
    }

    @Test
    void classifiesSwordAndAxeAsMelee() {
        assertEquals(DamageCategory.MELEE, DamageClassifier.classify(EntityDamageEvent.DamageCause.ENTITY_ATTACK, "DIAMOND_SWORD"));
        assertEquals(DamageCategory.MELEE, DamageClassifier.classify(EntityDamageEvent.DamageCause.ENTITY_ATTACK, "NETHERITE_AXE"));
    }

    @Test
    void classifiesExplosionCauseAsExplosion() {
        DamageCategory category = DamageClassifier.classify(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, "AIR");
        assertEquals(DamageCategory.EXPLOSION, category);
    }

    @Test
    void classifiesUnknownAsOther() {
        DamageCategory category = DamageClassifier.classify(EntityDamageEvent.DamageCause.CUSTOM, "BLAZE_ROD");
        assertEquals(DamageCategory.OTHER, category);
    }
}
