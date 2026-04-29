package com.tukuyomil032.engram.listener;

import com.tukuyomil032.engram.EngramPlugin;
import com.tukuyomil032.engram.session.BattleSession;
import com.tukuyomil032.engram.session.BattleSessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BattleTracker implements Listener {
    private static final long ALTITUDE_SAMPLE_INTERVAL_TICKS = 20L;

    private final EngramPlugin plugin;
    private final BattleSessionRegistry sessionRegistry;
    private final ConcurrentMap<UUID, AtomicInteger> crystalIndices = new ConcurrentHashMap<>();
    private BukkitTask altitudeSamplingTask;

    public BattleTracker(EngramPlugin plugin, BattleSessionRegistry sessionRegistry) {
        this.plugin = plugin;
        this.sessionRegistry = sessionRegistry;
    }

    public void startAltitudeSampling() {
        altitudeSamplingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (BattleSession session : sessionRegistry.allSessions()) {
                World world = Bukkit.getWorld(session.getWorldUid());
                if (world == null) {
                    continue;
                }
                for (Player player : world.getPlayers()) {
                    session.recordAltitudeSample(player.getUniqueId(), player.getLocation().getY());
                }
            }
        }, 0L, ALTITUDE_SAMPLE_INTERVAL_TICKS);
    }

    public void stopAltitudeSampling() {
        if (altitudeSamplingTask != null) {
            altitudeSamplingTask.cancel();
            altitudeSamplingTask = null;
        }
    }

    public void clearBattleState(UUID worldUid) {
        crystalIndices.remove(worldUid);
    }

    public void initializeBattleState(UUID worldUid) {
        crystalIndices.put(worldUid, new AtomicInteger(0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        BattleSession session = sessionRegistry.getSession(dragon.getWorld().getUID());
        if (session == null) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        Material material = attacker.getInventory().getItemInMainHand().getType();
        DamageCategory category = DamageClassifier.classify(event.getCause(), material.name());
        switch (category) {
            case BOW -> session.recordBowDamage(attacker.getUniqueId(), event.getFinalDamage());
            case MELEE -> session.recordMeleeDamage(attacker.getUniqueId(), event.getFinalDamage());
            case EXPLOSION -> session.recordExplosionDamage(attacker.getUniqueId(), event.getFinalDamage());
            case OTHER -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalDestroyed(EntityDamageByEntityEvent event) {
        if (event.getEntityType() != org.bukkit.entity.EntityType.END_CRYSTAL) {
            return;
        }

        Entity crystal = event.getEntity();
        if (event.getFinalDamage() < crystal.getHealth()) {
            return;
        }

        Player killer = resolveAttacker(event.getDamager());
        if (killer == null) {
            return;
        }

        UUID worldUid = crystal.getWorld().getUID();
        BattleSession session = sessionRegistry.getSession(worldUid);
        if (session == null) {
            return;
        }

        int crystalIndex = crystalIndices
            .computeIfAbsent(worldUid, ignored -> new AtomicInteger(0))
            .incrementAndGet();
        session.recordCrystalDestroyed(killer.getUniqueId(), System.currentTimeMillis(), crystalIndex);
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
