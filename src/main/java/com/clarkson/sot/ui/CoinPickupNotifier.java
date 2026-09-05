package com.clarkson.sot.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Batches coin pickups into a single action-bar message.
 *
 * <p>Coins are picked up in quick bursts — a stack of them sits in one room and a player walks
 * through the lot in a second or two. Sending one action bar per coin means each message replaces
 * the previous one before it can be read, so the player only ever sees the last coin's value. This
 * class instead keeps a running total per player: pickups landing within {@link #DEFAULT_WINDOW_MILLIS}
 * of the previous one extend the same batch, so "+5" followed by "+7" reads as "+12 coins (x2)".
 *
 * <p>A batch covers a fixed span from its first pickup rather than sliding forward with each one,
 * so a player collecting continuously gets a fresh message every window instead of one total that
 * grows all round. Expiry is checked on the next pickup, so no scheduled task is needed; re-sending
 * the action bar on every pickup also keeps it from fading while a burst is in progress.
 *
 * <p>The clock is injected so the batching can be tested without a running server; production code
 * uses the no-arg constructor.
 */
public final class CoinPickupNotifier {

    /** How long after a batch's first pickup further pickups still join the same message. */
    public static final long DEFAULT_WINDOW_MILLIS = 3000L;

    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<UUID, Batch> batches = new HashMap<>();

    public CoinPickupNotifier() {
        this(DEFAULT_WINDOW_MILLIS, System::currentTimeMillis);
    }

    public CoinPickupNotifier(long windowMillis, @NotNull LongSupplier clock) {
        if (windowMillis < 0) {
            throw new IllegalArgumentException("windowMillis cannot be negative");
        }
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /**
     * Adds {@code amount} to the player's current batch (starting a new one if the last pickup has
     * aged out) and shows the combined total on their action bar.
     *
     * @return the combined total now being displayed.
     */
    public int notifyPickup(@NotNull Player player, int amount) {
        Batch batch = accumulate(player.getUniqueId(), amount);
        player.sendActionBar(format(batch.total, batch.count));
        return batch.total;
    }

    /** Drops the player's pending batch, so the next pickup starts its own message. */
    public void reset(@NotNull UUID playerUUID) {
        batches.remove(playerUUID);
    }

    /** Drops every pending batch (game end, reset between rounds). */
    public void resetAll() {
        batches.clear();
    }

    /** The total currently batched for a player, or 0 if their batch has aged out. */
    public int getPendingTotal(@NotNull UUID playerUUID) {
        Batch batch = batches.get(playerUUID);
        return batch == null || isExpired(batch, clock.getAsLong()) ? 0 : batch.total;
    }

    private Batch accumulate(UUID playerUUID, int amount) {
        long now = clock.getAsLong();
        Batch batch = batches.get(playerUUID);
        if (batch == null || isExpired(batch, now)) {
            batch = new Batch();
            batch.startMillis = now;
            batches.put(playerUUID, batch);
        }
        batch.total += amount;
        batch.count++;
        return batch;
    }

    private boolean isExpired(Batch batch, long now) {
        return now - batch.startMillis >= windowMillis;
    }

    /**
     * Builds the action-bar text. A single pickup reads exactly as it did before batching existed;
     * a combined one carries an "(xN)" tail so the total is obviously several coins, not one big one.
     */
    static Component format(int total, int count) {
        Component message = Component.text("+" + total + " coins", NamedTextColor.GOLD);
        if (count > 1) {
            message = message.append(Component.text(" (x" + count + ")", NamedTextColor.YELLOW));
        }
        return message;
    }

    /** One player's in-progress burst of pickups. */
    private static final class Batch {
        private int total;
        private int count;
        private long startMillis;
    }
}
