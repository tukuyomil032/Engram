package com.tukuyomil032.engram.strategy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyConfigParserTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    @Test
    void parsesAllSupportedTriggerTypes() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("strategies.ANTI_ARCHER.description", "Archer counter");
        yaml.set("strategies.ANTI_ARCHER.phases", java.util.List.of(
            Map.of("trigger", "hp_percent:75", "skills", java.util.List.of("SkillHp")),
            Map.of("trigger", "timer_seconds:30", "skills", java.util.List.of("SkillTimer")),
            Map.of("trigger", "damage_chance:0.15", "skills", java.util.List.of("SkillChance")),
            Map.of("trigger", "interval_seconds:20", "skills", java.util.List.of("SkillInterval")),
            Map.of("trigger", "mm_skill:DragonSpecial", "skills", java.util.List.of("SkillMm"))
        ));

        Map<StrategyType, StrategyProfile> result = parser.parse(yaml);

        StrategyProfile profile = result.get(StrategyType.ANTI_ARCHER);
        assertEquals("Archer counter", profile.description());
        assertEquals(5, profile.phases().size());
        assertInstanceOf(PhaseTrigger.HpPercentTrigger.class, profile.phases().get(0).trigger());
        assertInstanceOf(PhaseTrigger.TimerSecondsTrigger.class, profile.phases().get(1).trigger());
        assertInstanceOf(PhaseTrigger.DamageChanceTrigger.class, profile.phases().get(2).trigger());
        assertInstanceOf(PhaseTrigger.IntervalSecondsTrigger.class, profile.phases().get(3).trigger());
        assertInstanceOf(PhaseTrigger.MmSkillTrigger.class, profile.phases().get(4).trigger());
    }

    @Test
    void rejectsMalformedTriggerSyntax() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("strategies.ANTI_ARCHER.description", "Archer counter");
        yaml.set("strategies.ANTI_ARCHER.phases", java.util.List.of(
            Map.of("trigger", "hp_percent", "skills", java.util.List.of("SkillHp"))
        ));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(yaml));
        assertTrue(exception.getMessage().contains("Malformed trigger 'hp_percent'"));
    }

    @Test
    void rejectsUnknownTriggerType() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("strategies.ANTI_ARCHER.description", "Archer counter");
        yaml.set("strategies.ANTI_ARCHER.phases", java.util.List.of(
            Map.of("trigger", "unsupported:1", "skills", java.util.List.of("SkillHp"))
        ));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(yaml));
        assertTrue(exception.getMessage().contains("unknown trigger type"));
    }

    @Test
    void rejectsNonFiniteDamageChance() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("strategies.ANTI_ARCHER.description", "Archer counter");
        yaml.set("strategies.ANTI_ARCHER.phases", java.util.List.of(
            Map.of("trigger", "damage_chance:NaN", "skills", java.util.List.of("SkillChance"))
        ));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(yaml));
        assertTrue(exception.getMessage().contains("Malformed trigger 'damage_chance:NaN'"));
    }
}
