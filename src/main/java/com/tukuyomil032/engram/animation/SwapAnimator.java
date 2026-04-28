package com.tukuyomil032.engram.animation;

import com.tukuyomil032.engram.EngramPlugin;
import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class SwapAnimator {
    private final EngramPlugin plugin;
    private final SwapTimeline timeline;

    public SwapAnimator(EngramPlugin plugin) {
        this.plugin = plugin;
        this.timeline = SwapTimeline.defaultTimeline();
    }

    public void start(EnderDragon vanillaDragon, String strategyName) {
        Location spawnLocation = vanillaDragon.getLocation().clone();
        World world = vanillaDragon.getWorld();
        String titleText = plugin.getEngramConfig().getTitleText();
        String awakeningSkill = plugin.getEngramConfig().getAwakeningSkill();

        new BukkitRunnable() {
            private int tick = 0;
            private Entity spawnedMythicDragon;

            @Override
            public void run() {
                SwapStep step = timeline.stepAt(tick);
                if (step != null) {
                    handleStep(step);
                }
                if (tick > 60) {
                    cancel();
                }
                tick++;
            }

            private void handleStep(SwapStep step) {
                switch (step) {
                    case LIGHTNING -> {
                        if (plugin.getEngramConfig().isLightningEnabled()) {
                            world.strikeLightningEffect(spawnLocation);
                        }
                    }
                    case SHOW_TITLE -> {
                        for (Player player : world.getPlayers()) {
                            player.sendTitle(titleText, "", 10, 40, 10);
                        }
                    }
                    case DRAGON_ROAR -> {
                        if (plugin.getEngramConfig().isSoundEnabled()) {
                            world.playSound(spawnLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
                        }
                    }
                    case REMOVE_VANILLA -> {
                        if (!vanillaDragon.isDead()) {
                            vanillaDragon.remove();
                        }
                    }
                    case SPAWN_MYTHIC -> spawnedMythicDragon = spawnMythicDragon(spawnLocation);
                    case APPLY_STRATEGY -> {
                        castAwakeningSkill(spawnedMythicDragon, awakeningSkill);
                        plugin.getSLF4JLogger().info("Swap animation finished with strategy '{}'.", strategyName);
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Entity spawnMythicDragon(Location location) {
        try {
            return MythicBukkit.inst().getAPIHelper()
                .spawnMythicMob(plugin.getEngramConfig().getMythicMobName(), location);
        } catch (InvalidMobTypeException exception) {
            plugin.getSLF4JLogger().error("Failed to spawn mythic dragon.", exception);
            return null;
        }
    }

    private void castAwakeningSkill(Entity dragon, String awakeningSkill) {
        if (dragon == null || awakeningSkill == null || awakeningSkill.isBlank()) {
            return;
        }
        boolean casted = MythicBukkit.inst().getAPIHelper().castSkill(dragon, awakeningSkill);
        if (!casted) {
            plugin.getSLF4JLogger().warn("Awakening skill '{}' failed to cast.", awakeningSkill);
        }
    }
}
