package com.tukuyomil032.engram.strategy;

import java.util.List;

public record StrategyProfile(StrategyType type, String description, List<StrategyPhase> phases) {

    public StrategyProfile {
        phases = List.copyOf(phases);
    }
}
