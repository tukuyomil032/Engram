package com.tukuyomil032.engram.strategy;

import java.util.List;

public record StrategyPhase(PhaseTrigger trigger, List<String> skills) {

    public StrategyPhase {
        skills = List.copyOf(skills);
    }
}
