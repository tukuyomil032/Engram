package com.tukuyomil032.engram.strategy;

public sealed interface PhaseTrigger
    permits PhaseTrigger.DamageChanceTrigger, PhaseTrigger.HpPercentTrigger, PhaseTrigger.IntervalSecondsTrigger,
    PhaseTrigger.MmSkillTrigger, PhaseTrigger.TimerSecondsTrigger {

    record HpPercentTrigger(int thresholdPercent) implements PhaseTrigger { }

    record TimerSecondsTrigger(int seconds) implements PhaseTrigger { }

    record DamageChanceTrigger(double chance) implements PhaseTrigger { }

    record IntervalSecondsTrigger(int seconds) implements PhaseTrigger { }

    record MmSkillTrigger(String skillName) implements PhaseTrigger { }
}
