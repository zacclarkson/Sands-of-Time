package com.clarkson.sot.main;

import com.clarkson.sot.utils.PlayerStatus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins down who the end-of-round teleport is allowed to move.
 *
 * <p>No mock server: {@link GameManager#returnsToLobbyAtGameEnd} is a pure decision, and it is the
 * one part of {@code endGameInternal} with a correctness requirement worth guarding. The teleport
 * it gates is queued with {@code runTask} in the same tick as the trapped teleport queued by
 * {@code handleTeamTimerEnd}, and runs after it — so letting a trapped player through here puts
 * them in the lobby instead of the trapped box, which is precisely the bug this guards against.
 */
class GameManagerTest {

    @Test
    void playersLockedInByTheTimerAreLeftInTheTrappedBox() {
        assertFalse(GameManager.returnsToLobbyAtGameEnd(PlayerStatus.TRAPPED_TIMER_OUT));
    }

    @Test
    void everyoneElseGoesBackToTheLobby() {
        for (PlayerStatus status : PlayerStatus.values()) {
            if (status == PlayerStatus.TRAPPED_TIMER_OUT) continue;
            assertTrue(GameManager.returnsToLobbyAtGameEnd(status),
                    status + " must still be returned to the lobby when the round ends");
        }
    }
}
