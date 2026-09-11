package com.zpkdxgames.plexonquests.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JournalNextActionTest {
    @Test
    void claimableWinsOverEveryOtherAction() {
        assertEquals(JournalNextAction.CLAIM_READY, JournalNextAction.resolve(1, true, 4));
    }

    @Test
    void trackedWinsWhenNothingIsClaimable() {
        assertEquals(JournalNextAction.CONTINUE_TRACKED, JournalNextAction.resolve(0, true, 4));
    }

    @Test
    void activeWinsWhenNothingIsClaimableOrTracked() {
        assertEquals(JournalNextAction.VIEW_ACTIVE, JournalNextAction.resolve(0, false, 2));
    }

    @Test
    void eligibleBrowseIsTheFallback() {
        assertEquals(JournalNextAction.BROWSE_ELIGIBLE, JournalNextAction.resolve(0, false, 0));
    }
}
