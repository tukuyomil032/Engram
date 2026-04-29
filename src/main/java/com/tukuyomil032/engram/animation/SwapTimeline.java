package com.tukuyomil032.engram.animation;

import java.util.Map;

public final class SwapTimeline {
    private final Map<Integer, SwapStep> stepsByTick;

    private SwapTimeline(Map<Integer, SwapStep> stepsByTick) {
        this.stepsByTick = Map.copyOf(stepsByTick);
    }

    public static SwapTimeline defaultTimeline() {
        return new SwapTimeline(Map.of(
            0, SwapStep.LIGHTNING,
            10, SwapStep.SHOW_TITLE,
            30, SwapStep.DRAGON_ROAR,
            50, SwapStep.REMOVE_VANILLA,
            51, SwapStep.SPAWN_MYTHIC,
            52, SwapStep.APPLY_STRATEGY
        ));
    }

    public SwapStep stepAt(int tick) {
        return stepsByTick.get(tick);
    }
}
