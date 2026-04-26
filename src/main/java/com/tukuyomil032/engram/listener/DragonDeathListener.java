package com.tukuyomil032.engram.listener;

import com.tukuyomil032.engram.EngramPlugin;
import com.tukuyomil032.engram.data.BattleRecord;
import com.tukuyomil032.engram.data.SQLiteDataStore;
import com.tukuyomil032.engram.session.BattleSession;
import com.tukuyomil032.engram.session.BattleSessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.sql.SQLException;
import java.util.UUID;

public final class DragonDeathListener implements Listener {
    private final EngramPlugin plugin;
    private final BattleSessionRegistry sessionRegistry;
    private final SQLiteDataStore dataStore;

    public DragonDeathListener(EngramPlugin plugin, BattleSessionRegistry sessionRegistry, SQLiteDataStore dataStore) {
        this.plugin = plugin;
        this.sessionRegistry = sessionRegistry;
        this.dataStore = dataStore;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        UUID worldUid = dragon.getWorld().getUID();
        BattleSession session = sessionRegistry.getSession(worldUid);
        if (session == null) {
            return;
        }

        BattleRecord battleRecord = session.finalizeBattle(System.currentTimeMillis(), true);
        sessionRegistry.removeSession(worldUid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dataStore.saveBattleRecord(battleRecord);
                plugin.getSLF4JLogger().info("Battle saved for world {}.", worldUid);
            } catch (SQLException exception) {
                plugin.getSLF4JLogger().error("Failed to save battle record for world {}.", worldUid, exception);
            }
        });
    }
}
