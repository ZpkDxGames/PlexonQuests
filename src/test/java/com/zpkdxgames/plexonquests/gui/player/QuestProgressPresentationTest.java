package com.zpkdxgames.plexonquests.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QuestProgressPresentationTest {
    @Test
    void rendersTenSegmentsAndRawRatio() {
        QuestProgressPresentation progress = QuestProgressPresentation.of(24, 40);
        assertEquals(60, progress.percentage());
        assertEquals("■■■■■■□□□□", progress.bar());
        assertEquals("24 / 40", progress.ratio());
    }

    @Test
    void clampsDisplayPercentageAboveOneHundred() {
        QuestProgressPresentation progress = QuestProgressPresentation.of(75, 40);
        assertEquals(100, progress.percentage());
        assertEquals("■■■■■■■■■■", progress.bar());
        assertEquals(75, progress.current());
        assertEquals(40, progress.required());
    }

    @Test
    void handlesZeroRequiredWithoutDivisionFailure() {
        QuestProgressPresentation progress = QuestProgressPresentation.of(0, 0);
        assertEquals(0, progress.percentage());
        assertEquals("□□□□□□□□□□", progress.bar());
    }
}
