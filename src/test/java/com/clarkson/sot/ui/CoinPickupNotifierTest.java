package com.clarkson.sot.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CoinPickupNotifier}. The clock is injected, so the batching window is
 * driven directly instead of sleeping; the action bar is observed on a mocked {@link Player}.
 */
class CoinPickupNotifierTest {

    private long now;
    private CoinPickupNotifier notifier;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        now = 1_000L;
        notifier = new CoinPickupNotifier(3000L, () -> now);
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
    }

    @Test
    void singlePickupShowsItsOwnValue() {
        assertEquals(5, notifier.notifyPickup(player, 5));
        assertEquals("+5 coins", lastActionBar());
    }

    @Test
    void pickupsWithinTheWindowAreCombined() {
        notifier.notifyPickup(player, 5);
        now += 1500L;
        assertEquals(12, notifier.notifyPickup(player, 7));
        assertEquals("+12 coins (x2)", lastActionBar());
    }

    @Test
    void everyPickupInABurstReSendsTheRunningTotal() {
        notifier.notifyPickup(player, 5);
        now += 500L;
        notifier.notifyPickup(player, 7);
        now += 500L;
        notifier.notifyPickup(player, 3);

        assertEquals(List.of("+5 coins", "+12 coins (x2)", "+15 coins (x3)"), allActionBars());
    }

    @Test
    void pickupAfterTheWindowStartsAFreshMessage() {
        notifier.notifyPickup(player, 5);
        now += 3000L;
        assertEquals(7, notifier.notifyPickup(player, 7), "batch should not carry past the window");
        assertEquals("+7 coins", lastActionBar());
    }

    @Test
    void windowIsMeasuredFromTheFirstPickupNotTheLast() {
        // A steady stream must not keep one message growing all round.
        notifier.notifyPickup(player, 5);
        now += 2000L;
        notifier.notifyPickup(player, 5);
        now += 2000L; // 4s after the batch started, only 2s after the last pickup.
        assertEquals(5, notifier.notifyPickup(player, 5));
    }

    @Test
    void batchesAreTrackedPerPlayer() {
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());

        notifier.notifyPickup(player, 5);
        assertEquals(7, notifier.notifyPickup(other, 7), "another player's coins must not be merged in");
        assertEquals(12, notifier.notifyPickup(player, 7));
    }

    @Test
    void resetEndsTheBatchSoTheNextPickupStandsAlone() {
        notifier.notifyPickup(player, 5);
        notifier.reset(playerId);
        assertEquals(7, notifier.notifyPickup(player, 7));
        assertEquals("+7 coins", lastActionBar());
    }

    @Test
    void resetAllClearsEveryBatch() {
        notifier.notifyPickup(player, 5);
        notifier.resetAll();
        assertEquals(0, notifier.getPendingTotal(playerId));
        assertEquals(7, notifier.notifyPickup(player, 7));
    }

    @Test
    void pendingTotalExpiresWithTheWindow() {
        notifier.notifyPickup(player, 5);
        assertEquals(5, notifier.getPendingTotal(playerId));
        now += 3000L;
        assertEquals(0, notifier.getPendingTotal(playerId));
    }

    @Test
    void unknownPlayerHasNoPendingTotal() {
        assertEquals(0, notifier.getPendingTotal(UUID.randomUUID()));
    }

    @Test
    void combinedMessageKeepsTheCoinTotalGold() {
        Component message = CoinPickupNotifier.format(12, 2);
        assertEquals(NamedTextColor.GOLD, message.color());
        assertEquals("+12 coins (x2)", PlainTextComponentSerializer.plainText().serialize(message));
    }

    @Test
    void negativeWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CoinPickupNotifier(-1L, () -> 0L));
    }

    private String lastActionBar() {
        List<String> sent = allActionBars();
        return sent.get(sent.size() - 1);
    }

    private List<String> allActionBars() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendActionBar(captor.capture());
        return captor.getAllValues().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
    }
}
