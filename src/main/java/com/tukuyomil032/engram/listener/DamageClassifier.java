package com.tukuyomil032.engram.listener;

import org.bukkit.event.entity.EntityDamageEvent;

public final class DamageClassifier {
    private DamageClassifier() {
    }

    public static DamageCategory classify(EntityDamageEvent.DamageCause cause, String mainHandMaterialName) {
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            return DamageCategory.BOW;
        }
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return DamageCategory.EXPLOSION;
        }
        if (mainHandMaterialName != null
            && (mainHandMaterialName.endsWith("_SWORD") || mainHandMaterialName.endsWith("_AXE"))) {
            return DamageCategory.MELEE;
        }
        return DamageCategory.OTHER;
    }
}
