package com.tukuyomil032.engram.listener;

import com.tukuyomil032.engram.session.BattleSessionRegistry;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.UUID;

public final class DragonSpawnListener implements Listener {
    private static final int DEFAULT_EXPECTED_CRYSTALS = 10;

    private final BattleSessionRegistry sessionRegistry;

    public DragonSpawnListener(BattleSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        UUID worldUid = dragon.getWorld().getUID();
        if (sessionRegistry.getSession(worldUid) != null) {
            return;
        }

        sessionRegistry.startSession(worldUid, "DEFAULT", System.currentTimeMillis(), DEFAULT_EXPECTED_CRYSTALS);
    }
}
