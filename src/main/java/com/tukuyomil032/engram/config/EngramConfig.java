package com.tukuyomil032.engram.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EngramConfig {
    private static final Logger logger = LoggerFactory.getLogger(EngramConfig.class);
    private static final int DEFAULT_WINDOW_SIZE = 100;
    private static final int DEFAULT_BLEND_THRESHOLD = 3;
    private static final double DEFAULT_BLEND_RATIO = 0.3D;
    private static final long DEFAULT_FAST_CRYSTAL_MS = 45_000L;
    private static final long DEFAULT_SPEEDRUN_MS = 120_000L;
    private static final String DEFAULT_MYTHIC_MOB_NAME = "EngramDragon";
    private static final String DEFAULT_AWAKENING_SKILL = "EngramAwakening";
    private static final int DEFAULT_ANIMATION_TICKS = 60;
    private static final boolean DEFAULT_PRESET_ENABLED = true;
    private static final boolean DEFAULT_LIGHTNING_ENABLED = true;
    private static final boolean DEFAULT_PARTICLES_ENABLED = true;
    private static final boolean DEFAULT_SOUND_ENABLED = true;
    private static final String DEFAULT_CHAT_MESSAGE = "&5The dragon remembers...";
    private static final String DEFAULT_TITLE = "???";

    private final int learningWindowSize;
    private final int individualBlendThreshold;
    private final double individualBlendRatio;
    private final long fastCrystalThresholdMs;
    private final long speedrunThresholdMs;
    private final String mythicMobName;
    private final String awakeningSkill;
    private final int animationTicks;
    private final boolean presetAnimationEnabled;
    private final boolean lightningEnabled;
    private final boolean particlesEnabled;
    private final boolean soundEnabled;
    private final String chatMessage;
    private final String titleText;

    private EngramConfig(
        int learningWindowSize,
        int individualBlendThreshold,
        double individualBlendRatio,
        long fastCrystalThresholdMs,
        long speedrunThresholdMs,
        String mythicMobName,
        String awakeningSkill,
        int animationTicks,
        boolean presetAnimationEnabled,
        boolean lightningEnabled,
        boolean particlesEnabled,
        boolean soundEnabled,
        String chatMessage,
        String titleText
    ) {
        this.learningWindowSize = learningWindowSize;
        this.individualBlendThreshold = individualBlendThreshold;
        this.individualBlendRatio = individualBlendRatio;
        this.fastCrystalThresholdMs = fastCrystalThresholdMs;
        this.speedrunThresholdMs = speedrunThresholdMs;
        this.mythicMobName = mythicMobName;
        this.awakeningSkill = awakeningSkill;
        this.animationTicks = animationTicks;
        this.presetAnimationEnabled = presetAnimationEnabled;
        this.lightningEnabled = lightningEnabled;
        this.particlesEnabled = particlesEnabled;
        this.soundEnabled = soundEnabled;
        this.chatMessage = chatMessage;
        this.titleText = titleText;
    }

    public static EngramConfig fromConfiguration(FileConfiguration config) {
        String mythicMobName = config.getString("dragon.mythicmob-name", DEFAULT_MYTHIC_MOB_NAME);
        if (mythicMobName != null) {
            mythicMobName = mythicMobName.trim();
        }
        if (mythicMobName == null || mythicMobName.isBlank()) {
            logger.warn("dragon.mythicmob-name is blank; falling back to default: {}", DEFAULT_MYTHIC_MOB_NAME);
            mythicMobName = DEFAULT_MYTHIC_MOB_NAME;
        }

        String awakeningSkill = config.getString("dragon.awakening-skill", DEFAULT_AWAKENING_SKILL);
        if (awakeningSkill != null) {
            awakeningSkill = awakeningSkill.trim();
        }
        if (awakeningSkill == null || awakeningSkill.isBlank()) {
            logger.warn("dragon.awakening-skill is blank; falling back to default: {}", DEFAULT_AWAKENING_SKILL);
            awakeningSkill = DEFAULT_AWAKENING_SKILL;
        }

        return new EngramConfig(
            Math.max(1, config.getInt("learning.window-size", DEFAULT_WINDOW_SIZE)),
            Math.max(1, config.getInt("learning.individual-blend-threshold", DEFAULT_BLEND_THRESHOLD)),
            clampBlendRatio(config.getDouble("learning.individual-blend-ratio", DEFAULT_BLEND_RATIO)),
            Math.max(1L, config.getLong("thresholds.fast-crystal-ms", DEFAULT_FAST_CRYSTAL_MS)),
            Math.max(1L, config.getLong("thresholds.speedrun-ms", DEFAULT_SPEEDRUN_MS)),
            mythicMobName,
            awakeningSkill,
            Math.max(1, config.getInt("dragon.animation-ticks", DEFAULT_ANIMATION_TICKS)),
            config.getBoolean("animation.preset-enabled", DEFAULT_PRESET_ENABLED),
            config.getBoolean("animation.lightning", DEFAULT_LIGHTNING_ENABLED),
            config.getBoolean("animation.particles", DEFAULT_PARTICLES_ENABLED),
            config.getBoolean("animation.sound", DEFAULT_SOUND_ENABLED),
            config.getString("animation.chat-message", DEFAULT_CHAT_MESSAGE),
            config.getString("animation.title", DEFAULT_TITLE)
        );
    }

    private static double clampBlendRatio(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        return Math.min(value, 1.0D);
    }

    public int getLearningWindowSize() {
        return learningWindowSize;
    }

    public int getIndividualBlendThreshold() {
        return individualBlendThreshold;
    }

    public double getIndividualBlendRatio() {
        return individualBlendRatio;
    }

    public long getFastCrystalThresholdMs() {
        return fastCrystalThresholdMs;
    }

    public long getSpeedrunThresholdMs() {
        return speedrunThresholdMs;
    }

    public String getMythicMobName() {
        return mythicMobName;
    }

    public String getAwakeningSkill() {
        return awakeningSkill;
    }

    public int getAnimationTicks() {
        return animationTicks;
    }

    public boolean isPresetAnimationEnabled() {
        return presetAnimationEnabled;
    }

    public boolean isLightningEnabled() {
        return lightningEnabled;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public String getChatMessage() {
        return chatMessage;
    }

    public String getTitleText() {
        return titleText;
    }
}
