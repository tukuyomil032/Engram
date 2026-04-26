package com.tukuyomil032.engram.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngramConfigTest {

    @Test
    void loadsConfiguredValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("learning.window-size", 42);
        yaml.set("learning.individual-blend-threshold", 5);
        yaml.set("learning.individual-blend-ratio", 0.4D);
        yaml.set("thresholds.fast-crystal-ms", 38000L);
        yaml.set("thresholds.speedrun-ms", 90000L);
        yaml.set("dragon.mythicmob-name", "CustomDragon");
        yaml.set("dragon.awakening-skill", "MyCustomAwakening");
        yaml.set("dragon.animation-ticks", 80);
        yaml.set("animation.preset-enabled", false);
        yaml.set("animation.lightning", false);
        yaml.set("animation.particles", true);
        yaml.set("animation.sound", false);
        yaml.set("animation.chat-message", "Remember me.");
        yaml.set("animation.title", "ENGRAM");

        EngramConfig config = EngramConfig.fromConfiguration(yaml);

        assertEquals(42, config.getLearningWindowSize());
        assertEquals(5, config.getIndividualBlendThreshold());
        assertEquals(0.4D, config.getIndividualBlendRatio());
        assertEquals(38000L, config.getFastCrystalThresholdMs());
        assertEquals(90000L, config.getSpeedrunThresholdMs());
        assertEquals("CustomDragon", config.getMythicMobName());
        assertEquals("MyCustomAwakening", config.getAwakeningSkill());
        assertEquals(80, config.getAnimationTicks());
        assertTrue(!config.isPresetAnimationEnabled());
        assertTrue(!config.isLightningEnabled());
        assertTrue(config.isParticlesEnabled());
        assertTrue(!config.isSoundEnabled());
        assertEquals("Remember me.", config.getChatMessage());
        assertEquals("ENGRAM", config.getTitleText());
    }

    @Test
    void fallsBackToDefaultsWhenUnset() {
        YamlConfiguration yaml = new YamlConfiguration();
        EngramConfig config = EngramConfig.fromConfiguration(yaml);

        assertEquals(100, config.getLearningWindowSize());
        assertEquals(3, config.getIndividualBlendThreshold());
        assertEquals(0.3D, config.getIndividualBlendRatio());
        assertEquals(45000L, config.getFastCrystalThresholdMs());
        assertEquals(120000L, config.getSpeedrunThresholdMs());
        assertEquals("EngramDragon", config.getMythicMobName());
        assertEquals("EngramAwakening", config.getAwakeningSkill());
        assertEquals(60, config.getAnimationTicks());
        assertTrue(config.isPresetAnimationEnabled());
        assertTrue(config.isLightningEnabled());
        assertTrue(config.isParticlesEnabled());
        assertTrue(config.isSoundEnabled());
        assertEquals("&5The dragon remembers...", config.getChatMessage());
        assertEquals("???", config.getTitleText());
    }
}
