package com.tukuyomil032.engram.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SwapTimelineTest {

    @Test
    void defaultTimelineMatchesRequiredMilestones() {
        SwapTimeline timeline = SwapTimeline.defaultTimeline();

        assertEquals(SwapStep.LIGHTNING, timeline.stepAt(0));
        assertEquals(SwapStep.SHOW_TITLE, timeline.stepAt(10));
        assertEquals(SwapStep.DRAGON_ROAR, timeline.stepAt(30));
        assertEquals(SwapStep.REMOVE_VANILLA, timeline.stepAt(50));
        assertEquals(SwapStep.SPAWN_MYTHIC, timeline.stepAt(51));
        assertEquals(SwapStep.APPLY_STRATEGY, timeline.stepAt(52));
        assertNull(timeline.stepAt(53));
    }
}
