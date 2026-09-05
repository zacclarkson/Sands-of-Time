package com.clarkson.sot.main;

import com.clarkson.sot.utils.PlayerStatus;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins down who the end-of-round teleport is allowed to move.
 *
 * <p>No mock server: {@link GameManager#returnsToLobbyAtGameEnd} is a pure decision, and it is the
 * one part of {@code endGameInternal} with a correctness requirement worth guarding. The teleport
 * it gates is queued with {@code runTask} in the same tick as the trapped teleport queued by
 * {@code handleTeamTimerEnd}, and runs after it — so letting a trapped player through here puts
 * them in the lobby instead of the trapped box, which is precisely the bug this guards against.
 *
 * <p>Also pins {@link GameManager#canResetFrom}, the gate on the ENDED → SETUP transition that lets
 * a server play consecutive rounds. Both are static on purpose: building a real {@code GameManager}
 * means building every manager and loading segment templates off disk, which these tests avoid.
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

    /** ENDED used to be a dead end: without this transition a server could only ever play once. */
    @Test
    void aFinishedRoundCanBeResetSoAnotherCanBePlayed() {
        assertTrue(GameManager.canResetFrom(GameState.ENDED));
    }

    /** Re-clearing an idle game is harmless, so an operator never has to guess whether it is safe. */
    @Test
    void resettingAnIdleGameIsAllowed() {
        assertTrue(GameManager.canResetFrom(GameState.SETUP));
    }

    /**
     * A live round has players in a dungeon and timers on the clock; tearing that down underneath
     * them would strand them there. {@code /sot end} first.
     */
    @Test
    void resetIsRefusedWhileARoundIsLive() {
        Set<GameState> live = EnumSet.of(GameState.COUNTDOWN, GameState.RUNNING, GameState.PAUSED);
        for (GameState state : live) {
            assertFalse(GameManager.canResetFrom(state),
                    state + " is a live round and must be ended before it can be reset");
        }
        // Every state is either resettable or live — no state may fall through both.
        for (GameState state : GameState.values()) {
            assertEquals(!live.contains(state), GameManager.canResetFrom(state),
                    "unclassified game state: " + state);
        }
    }

    /**
     * {@link GameManager#isRoundLive} and {@link GameManager#canResetFrom} are written as two
     * separate whitelists rather than one negating the other, so that a newly added
     * {@link GameState} is claimed by neither and fails here — instead of silently defaulting to
     * "resettable" on one side and "unprotected from block breaking" on the other.
     */
    @Test
    void everyStateIsEitherLiveOrResettable() {
        for (GameState state : GameState.values()) {
            assertEquals(!GameManager.canResetFrom(state), GameManager.isRoundLive(state),
                    "unclassified game state: " + state);
        }
    }

    /**
     * What {@code BlockProtectionListener} keys off. The countdown counts: the hub segment's baked
     * sand shaft is real sand in the world from the moment the dungeon is pasted, which is before
     * the round starts. SETUP and ENDED must stay unprotected so the segment builder tools keep
     * working between rounds.
     */
    @Test
    void blocksAreProtectedFromTheCountdownUntilTheRoundEnds() {
        assertTrue(GameManager.isRoundLive(GameState.COUNTDOWN));
        assertTrue(GameManager.isRoundLive(GameState.RUNNING));
        assertTrue(GameManager.isRoundLive(GameState.PAUSED));
        assertFalse(GameManager.isRoundLive(GameState.SETUP));
        assertFalse(GameManager.isRoundLive(GameState.ENDED));
    }
}
