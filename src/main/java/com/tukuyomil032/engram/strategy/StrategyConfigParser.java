package com.tukuyomil032.engram.strategy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StrategyConfigParser {

    public @NotNull Map<StrategyType, StrategyProfile> parse(@NotNull YamlConfiguration config) {
        ConfigurationSection strategiesSection = config.getConfigurationSection("strategies");
        if (strategiesSection == null) {
            throw new IllegalArgumentException("Missing required section: strategies");
        }

        Map<StrategyType, StrategyProfile> profiles = new EnumMap<>(StrategyType.class);
        for (String rawStrategyKey : strategiesSection.getKeys(false)) {
            StrategyType strategyType = parseStrategyType(rawStrategyKey);

            if (profiles.containsKey(strategyType)) {
                throw new IllegalArgumentException(
                    "Duplicate strategy type '%s' detected for raw key '%s' (already defined by another key)"
                        .formatted(strategyType, rawStrategyKey)
                );
            }

            ConfigurationSection strategySection = strategiesSection.getConfigurationSection(rawStrategyKey);
            if (strategySection == null) {
                throw new IllegalArgumentException("Strategy '%s' must be a configuration section".formatted(rawStrategyKey));
            }

            String description = strategySection.getString("description", "");
            List<?> rawPhases = strategySection.getList("phases");
            if (rawPhases == null) {
                throw new IllegalArgumentException("Strategy '%s' is missing required phases list".formatted(rawStrategyKey));
            }

            List<StrategyPhase> phases = parsePhases(rawStrategyKey, rawPhases);
            profiles.put(strategyType, new StrategyProfile(strategyType, description, phases));
        }
        return Map.copyOf(profiles);
    }

    private List<StrategyPhase> parsePhases(String strategyKey, List<?> rawPhases) {
        List<StrategyPhase> phases = new ArrayList<>(rawPhases.size());
        for (int index = 0; index < rawPhases.size(); index++) {
            Object rawPhase = rawPhases.get(index);
            if (!(rawPhase instanceof Map<?, ?> phaseMap)) {
                throw new IllegalArgumentException("Strategy '%s' phase #%d must be a map".formatted(strategyKey, index + 1));
            }

            Object rawTrigger = phaseMap.get("trigger");
            if (!(rawTrigger instanceof String triggerText) || triggerText.isBlank()) {
                throw new IllegalArgumentException(
                    "Strategy '%s' phase #%d requires non-empty trigger".formatted(strategyKey, index + 1)
                );
            }

            Object rawSkills = phaseMap.get("skills");
            if (!(rawSkills instanceof List<?> skillsList) || skillsList.isEmpty()) {
                throw new IllegalArgumentException(
                    "Strategy '%s' phase #%d requires at least one skill".formatted(strategyKey, index + 1)
                );
            }

            List<String> skills = new ArrayList<>(skillsList.size());
            for (Object skill : skillsList) {
                if (!(skill instanceof String skillName) || skillName.isBlank()) {
                    throw new IllegalArgumentException(
                        "Strategy '%s' phase #%d contains invalid skill name".formatted(strategyKey, index + 1)
                    );
                }
                skills.add(skillName);
            }

            PhaseTrigger trigger = parseTrigger(strategyKey, index + 1, triggerText);
            phases.add(new StrategyPhase(trigger, skills));
        }
        return List.copyOf(phases);
    }

    private StrategyType parseStrategyType(String rawStrategyKey) {
        try {
            return StrategyType.valueOf(rawStrategyKey.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown strategy key: %s".formatted(rawStrategyKey), exception);
        }
    }

    private PhaseTrigger parseTrigger(String strategyKey, int phaseNumber, String triggerText) {
        String[] parts = triggerText.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw invalidTrigger(strategyKey, phaseNumber, triggerText, "expected syntax <type>:<value>");
        }

        String triggerType = parts[0].trim().toLowerCase(Locale.ROOT);
        String triggerValue = parts[1].trim();
        try {
            return switch (triggerType) {
                case "hp_percent" -> new PhaseTrigger.HpPercentTrigger(parsePercent(triggerValue));
                case "timer_seconds" -> new PhaseTrigger.TimerSecondsTrigger(parsePositiveInt(triggerValue, "timer_seconds"));
                case "damage_chance" -> new PhaseTrigger.DamageChanceTrigger(parseChance(triggerValue));
                case "interval_seconds" -> new PhaseTrigger.IntervalSecondsTrigger(
                    parsePositiveInt(triggerValue, "interval_seconds")
                );
                case "mm_skill" -> parseMmSkill(triggerValue, strategyKey, phaseNumber, triggerText);
                default -> throw invalidTrigger(strategyKey, phaseNumber, triggerText, "unknown trigger type '%s'".formatted(triggerType));
            };
        } catch (NumberFormatException exception) {
            throw invalidTrigger(strategyKey, phaseNumber, triggerText, "numeric value is malformed", exception);
        }
    }

    private PhaseTrigger parseMmSkill(String triggerValue, String strategyKey, int phaseNumber, String triggerText) {
        if (triggerValue.isBlank()) {
            throw invalidTrigger(strategyKey, phaseNumber, triggerText, "mm_skill requires non-empty skill name");
        }
        return new PhaseTrigger.MmSkillTrigger(triggerValue);
    }

    private int parsePercent(String value) {
        int percent = Integer.parseInt(value);
        if (percent < 0 || percent > 100) {
            throw new NumberFormatException("percent must be between 0 and 100");
        }
        return percent;
    }

    private int parsePositiveInt(String value, String triggerType) {
        int number = Integer.parseInt(value);
        if (number <= 0) {
            throw new NumberFormatException(triggerType + " must be greater than 0");
        }
        return number;
    }

    private double parseChance(String value) {
        double chance = Double.parseDouble(value);
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            throw new NumberFormatException("damage_chance must be between 0.0 and 1.0");
        }
        return chance;
    }

    private IllegalArgumentException invalidTrigger(String strategyKey, int phaseNumber, String triggerText, String reason) {
        return new IllegalArgumentException(
            "Malformed trigger '%s' in strategy '%s' phase #%d: %s".formatted(triggerText, strategyKey, phaseNumber, reason)
        );
    }

    private IllegalArgumentException invalidTrigger(
        String strategyKey,
        int phaseNumber,
        String triggerText,
        String reason,
        Exception cause
    ) {
        return new IllegalArgumentException(
            "Malformed trigger '%s' in strategy '%s' phase #%d: %s".formatted(triggerText, strategyKey, phaseNumber, reason),
            cause
        );
    }
}
